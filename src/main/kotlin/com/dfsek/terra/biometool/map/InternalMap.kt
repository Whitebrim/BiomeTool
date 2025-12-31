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
    
    private lateinit var viewport: Viewport
    
    fun updateViewport(x: Double, y: Double, worldWidth: Double, worldHeight: Double) {
        viewport = Viewport(x, y, worldWidth, worldHeight)
        shouldUpdate()
    }
    
    private fun updateTiles() {
        if (!::viewport.isInitialized) return
        
        val xMinTile = floorToInt(viewport.x / tileSize) - TILE_BUFFER
        val yMinTile = floorToInt(viewport.y / tileSize) - TILE_BUFFER
        val xMaxTile = ceilToInt((viewport.x + viewport.width) / tileSize) + TILE_BUFFER
        val yMaxTile = ceilToInt((viewport.y + viewport.height) / tileSize) + TILE_BUFFER
        
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
        
        cleanupTiles(xMinTile - CLEANUP_BUFFER, yMinTile - CLEANUP_BUFFER, 
                     xMaxTile + CLEANUP_BUFFER, yMaxTile + CLEANUP_BUFFER)
    }
    
    private fun cleanupTiles(xMin: Int, yMin: Int, xMax: Int, yMax: Int) {
        val toRemove = mutableListOf<MapTile>()
        
        for (child in children) {
            if (child !is MapTile) continue
            
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
    
    private data class Viewport(val x: Double, val y: Double, val width: Double, val height: Double)
    
    companion object {
        private const val TILE_BUFFER = 1
        private const val CLEANUP_BUFFER = 1
    }
}
