package com.mapconductor.template

import androidx.compose.ui.geometry.Size
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapViewHolderInterface
import com.mapconductor.core.map.VisibleRegion
import com.mapconductor.core.marker.MarkerEntity
import com.mapconductor.core.marker.MarkerEntityInterface
import com.mapconductor.core.marker.MarkerManager
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.core.marker.MarkerRenderingStrategyInterface
import com.mapconductor.core.marker.MarkerState
import com.mapconductor.core.marker.OnMarkerEventHandler
import com.mapconductor.core.marker.StrategyMarkerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `clearOverlays()` は、クラスタリングなどの拡張モジュールが registerOverlayController で
 * 登録した StrategyMarkerController も消す。ドライバーを書くときは
 * `clearStrategyMarkerControllers()` を clearOverlays() から呼ぶこと。
 */
class TemplateClearOverlaysStrategyTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun newDriver(): TemplateMapViewController {
        val map = TemplateMap()
        map.viewportSize = Size(1000f, 1000f)
        val holder = TemplateMapViewHolder(TemplateMapSurface(map), map)
        return TemplateMapViewController(
            holder = holder,
            markerController = TemplateMarkerController(TemplateMarkerRenderer(holder, scope)),
            polylineController = TemplatePolylineController(TemplatePolylineRenderer(holder)),
            polygonController = TemplatePolygonController(TemplatePolygonRenderer(holder)),
            circleController = TemplateCircleController(TemplateCircleRenderer(holder)),
            groundImageController = TemplateGroundImageController(TemplateGroundImageRenderer(holder)),
            rasterLayerController = TemplateRasterLayerController(TemplateRasterLayerRenderer(holder, scope)),
            mainCoroutine = scope,
            defaultCoroutine = scope,
        )
    }

    private class GlRenderer(
        private val manager: MarkerManager<String>,
    ) : MarkerOverlayRendererInterface<String> {
        var drawn: List<String> = emptyList()
        override var animateStartListener: OnMarkerEventHandler? = null
        override var animateEndListener: OnMarkerEventHandler? = null
        override val holder: MapViewHolderInterface<*, *>
            get() = throw UnsupportedOperationException("not used")

        override suspend fun onAdd(data: List<MarkerOverlayRendererInterface.AddParamsInterface>) = data.map { it.state.id }

        override suspend fun onChange(data: List<MarkerOverlayRendererInterface.ChangeParamsInterface<String>>) =
            data.map { it.current.state.id }

        override suspend fun onRemove(data: List<MarkerEntityInterface<String>>) = Unit

        override suspend fun onAnimate(entity: MarkerEntityInterface<String>) = Unit

        override suspend fun onPostProcess() {
            drawn = manager.allEntities().map { it.state.id }
        }
    }

    private class DrawAllStrategy : MarkerRenderingStrategyInterface<String> {
        override val markerManager: MarkerManager<String> = MarkerManager.defaultManager()

        override fun clear() = markerManager.clear()

        override suspend fun onUpdate(
            state: MarkerState,
            viewport: GeoRectBounds,
            renderer: MarkerOverlayRendererInterface<String>,
        ) = true

        override suspend fun onAdd(
            data: List<MarkerState>,
            viewport: GeoRectBounds,
            renderer: MarkerOverlayRendererInterface<String>,
        ): Boolean {
            data.forEach { markerManager.registerEntity(MarkerEntity(marker = it.id, state = it, isRendered = true)) }
            renderer.onPostProcess()
            return true
        }

        override suspend fun onCameraChanged(
            cameraPosition: MapCameraPosition,
            renderer: MarkerOverlayRendererInterface<String>,
        ) = Unit
    }

    @Test
    fun `clearOverlays は登録された Strategy のマーカーも消す`() =
        runBlocking {
            val driver = newDriver()
            val strategy = DrawAllStrategy()
            val renderer = GlRenderer(strategy.markerManager)
            val controller = StrategyMarkerController(strategy, renderer)
            driver.registerOverlayController(controller)
            val bounds = GeoRectBounds(GeoPoint(34.0, 138.0), GeoPoint(36.0, 140.0))
            controller.onCameraChanged(
                MapCameraPosition(
                    position = GeoPoint(35.0, 139.0),
                    zoom = 10.0,
                    visibleRegion = VisibleRegion(bounds, null, null, null, null),
                ),
            )
            controller.add(listOf(MarkerState(position = GeoPoint(35.0, 139.0), id = "s1")))
            assertEquals(listOf("s1"), renderer.drawn)

            driver.clearOverlays()

            assertEquals(emptyList<String>(), renderer.drawn)
        }
}
