package com.dfsek.terra.biometool.map

import com.dfsek.terra.biometool.BiomeImageGenerator
import com.dfsek.terra.biometool.util.ceilToInt
import com.dfsek.terra.biometool.util.floorToInt
import com.dfsek.terra.biometool.util.squash
import java.lang.ref.SoftReference
import javafx.application.Platform
import javafx.scene.Group
import kotlinx.coroutines.CoroutineScope

class InternalMap(
    val scope: CoroutineScope,
    val tileSize: Int,
    val tileGenerator: BiomeImageGenerator
                 ) : Group() {
    private val tiles: MutableMap<Long, SoftReference<MapTile>> = mutableMapOf()
    
    private var isShouldUpdate: Boolean = true
    
    // Viewport parameters (in world coordinates)
    private var viewportX: Double = 0.0
    private var viewportY: Double = 0.0
    private var viewportWidth: Double = 800.0  // Default non-zero values
    private var viewportHeight: Double = 600.0
    private var viewportZoom: Double = 1.0
    
    fun updateViewport(x: Double, y: Double, screenWidth: Double, screenHeight: Double, zoom: Double) {
        viewportX = x
        viewportY = y
        // Convert screen dimensions to world dimensions
        viewportWidth = if (screenWidth > 0) screenWidth / zoom else 800.0
        viewportHeight = if (screenHeight > 0) screenHeight / zoom else 600.0
        viewportZoom = zoom
        
        shouldUpdate()
    }
    
    private fun updateTiles() {
        // Calculate visible tile range in world coordinates
        // Tiles are positioned at (tileX * tileSize, tileY * tileSize) in world coords
        
        val xMinTile = floorToInt(viewportX / tileSize) - 1
        val yMinTile = floorToInt(viewportY / tileSize) - 1
        val xMaxTile = ceilToInt((viewportX + viewportWidth) / tileSize) + 1
        val yMaxTile = ceilToInt((viewportY + viewportHeight) / tileSize) + 1
        
        for (tileX in xMinTile..xMaxTile) {
            for (tileY in yMinTile..yMaxTile) {
                val key = squash(tileX, tileY)
                
                val ref = tiles[key]?.get()
                
                val tile = if (ref == null) {
                    val tile = MapTile(MapTilePoint(tileX, tileY), this)
                    tiles[key] = SoftReference(tile)
                    tile
                } else {
                    ref
                }
                
                if (!children.contains(tile)) {
                    children.add(tile)
                }
            }
        }
        
        cleanupTiles(xMinTile - 2, yMinTile - 2, xMaxTile + 2, yMaxTile + 2)
    }
    
    private fun cleanupTiles(xMin: Int, yMin: Int, xMax: Int, yMax: Int) {
        val toRemove = mutableListOf<MapTile>()
        
        for (child in children) {
            if (child !is MapTile)
                continue
            
            val tileX = (child.translateX / tileSize).toInt()
            val tileY = (child.translateY / tileSize).toInt()
            
            if (tileX < xMin || tileX > xMax || tileY < yMin || tileY > yMax) {
                toRemove.add(child)
            }
        }
        
        children.removeAll(toRemove)
    }
    
    override fun layoutChildren() {
        if (isShouldUpdate) {
            updateTiles()
            isShouldUpdate = false
        }
        super.layoutChildren()
    }
    
    fun shouldUpdate() {
        isShouldUpdate = true
        this.isNeedsLayout = true
        Platform.requestNextPulse()
    }
}
