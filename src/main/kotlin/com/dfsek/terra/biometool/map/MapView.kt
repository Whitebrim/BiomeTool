package com.dfsek.terra.biometool.map

import com.dfsek.terra.biometool.BiomeImageGenerator
import javafx.scene.layout.Region
import javafx.scene.shape.Rectangle
import javafx.scene.transform.Scale
import kotlinx.coroutines.CoroutineScope
import tornadofx.onChange
import kotlin.math.pow

class MapView(
    scope: CoroutineScope,
    tileGenerator: BiomeImageGenerator,
    private val tileSize: Int = 128,
             ) : Region() {
    private val map = InternalMap(scope, tileSize, tileGenerator)
    
    private val clip = Rectangle()
    
    var x = 0.0
        private set
    var y = 0.0
        private set
    
    private var zoomLevel = 0.0
    
    val zoom: Double
        get() = 2.0.pow(zoomLevel)
    
    val seed = tileGenerator.seed
    
    val configPack = tileGenerator.configPack
    
    private val scaleTransform = Scale(1.0, 1.0, 0.0, 0.0)
    
    init {
        children += map
        map.transforms.add(scaleTransform)
        
        maxHeight = Double.MAX_VALUE
        maxWidth = Double.MAX_VALUE
        
        prefHeightProperty().onChange {
            map.prefHeight(it)
        }
        prefWidthProperty().onChange {
            map.prefWidth(it)
        }
        
        var mouseDragX = 0.0
        var mouseDragY = 0.0
        
        setClip(clip)
        
        setOnMousePressed { event ->
            mouseDragX = event.x
            mouseDragY = event.y
        }
        
        setOnMouseDragged { event ->
            val deltaScreenX = event.x - mouseDragX
            val deltaScreenY = event.y - mouseDragY
            
            x -= deltaScreenX / zoom
            y -= deltaScreenY / zoom
            
            mouseDragX = event.x
            mouseDragY = event.y
            
            updateMapTransform()
        }
        
        setOnScroll { event ->
            val oldZoom = zoom
            
            zoomLevel = (zoomLevel + if (event.deltaY > 0) ZOOM_STEP else -ZOOM_STEP)
                .coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL)
            
            val newZoom = zoom
            
            if (oldZoom != newZoom) {
                val centerX = width / 2
                val centerY = height / 2
                
                val worldCenterX = x + centerX / oldZoom
                val worldCenterY = y + centerY / oldZoom
                
                x = worldCenterX - centerX / newZoom
                y = worldCenterY - centerY / newZoom
                
                updateMapTransform()
            }
        }
    }
    
    private fun updateMapTransform() {
        val currentZoom = zoom
        
        scaleTransform.x = currentZoom
        scaleTransform.y = currentZoom
        
        map.translateX = -x * currentZoom
        map.translateY = -y * currentZoom
        
        map.updateViewport(x, y, width / currentZoom, height / currentZoom)
    }
    
    override fun layoutChildren() {
        super.layoutChildren()
        
        clip.width = width
        clip.height = height
        
        if (width > 0 && height > 0) {
            updateMapTransform()
        }
    }
    
    companion object {
        private const val MIN_ZOOM_LEVEL = -3.0  // 2^(-3) = 0.125
        private const val MAX_ZOOM_LEVEL = 3.0   // 2^3 = 8.0
        private const val ZOOM_STEP = 0.2
    }
}
