package com.dfsek.terra.biometool.map

import com.dfsek.terra.biometool.BiomeImageGenerator
import javafx.scene.layout.Region
import javafx.scene.shape.Rectangle
import javafx.scene.transform.Scale
import kotlinx.coroutines.CoroutineScope
import tornadofx.onChange

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
    
    var zoom = 1.0
        private set
    
    val seed = tileGenerator.seed
    
    val configPack = tileGenerator.configPack
    
    companion object {
        private const val MIN_ZOOM = 0.125
        private const val MAX_ZOOM = 8.0
        private const val ZOOM_FACTOR = 1.15
    }
    
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
            // Delta in screen pixels
            val deltaScreenX = event.x - mouseDragX
            val deltaScreenY = event.y - mouseDragY
            
            // Convert to world coordinates (divide by zoom)
            x -= deltaScreenX / zoom
            y -= deltaScreenY / zoom
            
            mouseDragX = event.x
            mouseDragY = event.y
            
            updateMapTransform()
        }
        
        setOnScroll { event ->
            val oldZoom = zoom
            
            // Calculate new zoom level
            zoom = if (event.deltaY > 0) {
                (zoom * ZOOM_FACTOR).coerceAtMost(MAX_ZOOM)
            } else {
                (zoom / ZOOM_FACTOR).coerceAtLeast(MIN_ZOOM)
            }
            
            if (oldZoom != zoom) {
                // Zoom from center of screen
                val centerX = width / 2
                val centerY = height / 2
                
                // World coordinates of the center before zoom
                val worldCenterX = x + centerX / oldZoom
                val worldCenterY = y + centerY / oldZoom
                
                // Adjust x, y so the same world point stays at center after zoom
                x = worldCenterX - centerX / zoom
                y = worldCenterY - centerY / zoom
                
                updateMapTransform()
            }
        }
    }
    
    private fun updateMapTransform() {
        // Transform logic:
        // We want: screenPos = (worldPos - viewportOffset) * zoom
        // 
        // Using Scale transform with pivot (0,0) applied first via transforms list,
        // then translateX/Y applied after:
        // screenPos = worldPos * zoom + translateX
        // 
        // So: translateX = -viewportOffset * zoom
        
        scaleTransform.x = zoom
        scaleTransform.y = zoom
        
        map.translateX = -x * zoom
        map.translateY = -y * zoom
        
        // Update visible area calculation in InternalMap
        if (width > 0 && height > 0) {
            map.updateViewport(x, y, width, height, zoom)
        }
    }
    
    override fun layoutChildren() {
        super.layoutChildren()
        
        clip.width = width
        clip.height = height
        
        // Always update transform on layout to ensure initial render works
        updateMapTransform()
    }
}
