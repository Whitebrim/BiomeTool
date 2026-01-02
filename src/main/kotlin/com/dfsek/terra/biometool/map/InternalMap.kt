package com.dfsek.terra.biometool.map

import com.dfsek.terra.biometool.BiomeImageGenerator
import com.dfsek.terra.biometool.util.ceilToInt
import com.dfsek.terra.biometool.util.floorToInt
import com.dfsek.terra.biometool.util.squash
import javafx.application.Platform
import javafx.scene.Group
import javafx.scene.image.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class InternalMap(
    val scope: CoroutineScope,
    val tileSize: Int,
    val tileGenerator: BiomeImageGenerator
                 ) : Group() {
    
    private val tileCache: MutableMap<TileKey, CachedTile> = mutableMapOf()
    private val displayedTiles: MutableMap<Long, DisplayedTile> = mutableMapOf()
    private val pendingJobs: MutableMap<TileKey, Job> = mutableMapOf()
    
    private var isShouldUpdate: Boolean = true
    private var currentLod: Int = 0
    
    private lateinit var viewport: Viewport
    private var currentVisibleKeys: Set<Long> = emptySet()
    
    fun updateViewport(x: Double, y: Double, worldWidth: Double, worldHeight: Double, lod: Int) {
        viewport = Viewport(x, y, worldWidth, worldHeight)
        currentLod = lod
        shouldUpdate()
    }
    
    private fun updateTiles() {
        if (!::viewport.isInitialized) return
        
        val xMinTile = floorToInt(viewport.x / tileSize) - TILE_BUFFER
        val yMinTile = floorToInt(viewport.y / tileSize) - TILE_BUFFER
        val xMaxTile = ceilToInt((viewport.x + viewport.width) / tileSize) + TILE_BUFFER
        val yMaxTile = ceilToInt((viewport.y + viewport.height) / tileSize) + TILE_BUFFER
        
        val visiblePositions = mutableSetOf<Long>()
        val toGenerate = mutableListOf<GenerationTask>()
        
        for (tileX in xMinTile..xMaxTile) {
            for (tileY in yMinTile..yMaxTile) {
                val posKey = squash(tileX, tileY)
                visiblePositions.add(posKey)
                
                val bestCached = findBestCachedTile(tileX, tileY)
                val currentDisplayed = displayedTiles[posKey]
                
                if (bestCached != null) {
                    if (currentDisplayed == null || currentDisplayed.lod > bestCached.lod) {
                        currentDisplayed?.imageView?.let { children.remove(it) }
                        
                        if (!children.contains(bestCached.imageView)) {
                            children.add(bestCached.imageView)
                        }
                        displayedTiles[posKey] = DisplayedTile(bestCached.imageView, bestCached.lod)
                    }
                    
                    if (bestCached.lod > currentLod && !hasPendingJob(tileX, tileY, currentLod)) {
                        toGenerate.add(GenerationTask(tileX, tileY, currentLod, priority = 1))
                    }
                } else {
                    if (!hasPendingJob(tileX, tileY, currentLod)) {
                        toGenerate.add(GenerationTask(tileX, tileY, currentLod, priority = 0))
                    }
                }
            }
        }
        
        currentVisibleKeys = visiblePositions
        
        cancelInvisibleJobs()
        
        // Sort by priority (0 = no tile yet, 1 = needs improvement)
        toGenerate.sortBy { it.priority }
        
        for (task in toGenerate) {
            scheduleGeneration(task.tileX, task.tileY, task.lod)
        }
        
        cleanupDisplayed(visiblePositions)
    }
    
    private fun findBestCachedTile(tileX: Int, tileY: Int): CachedTile? {
        var best: CachedTile? = null
        for (lod in 0..MAX_LOD) {
            val cached = tileCache[TileKey(tileX, tileY, lod)]
            if (cached != null && (best == null || cached.lod < best.lod)) {
                best = cached
            }
        }
        return best
    }
    
    private fun hasPendingJob(tileX: Int, tileY: Int, lod: Int): Boolean {
        return pendingJobs.containsKey(TileKey(tileX, tileY, lod))
    }
    
    private fun scheduleGeneration(tileX: Int, tileY: Int, lod: Int) {
        val key = TileKey(tileX, tileY, lod)
        
        if (tileCache.containsKey(key)) return
        if (pendingJobs.containsKey(key)) return
        
        val job = scope.launch {
            try {
                val posKey = squash(tileX, tileY)
                
                if (posKey !in currentVisibleKeys) return@launch
                
                val point = MapTilePoint(tileX, tileY)
                val image = tileGenerator.generateBiomeImage(point, tileSize, lod)
                
                val imageView = ImageView(image).apply {
                    fitWidth = tileSize.toDouble()
                    fitHeight = tileSize.toDouble()
                    isPreserveRatio = false
                    isMouseTransparent = true
                    translateX = (tileX * tileSize).toDouble()
                    translateY = (tileY * tileSize).toDouble()
                }
                
                val cachedTile = CachedTile(imageView, lod)
                
                Platform.runLater {
                    tileCache[key] = cachedTile
                    pendingJobs.remove(key)
                    
                    if (posKey in currentVisibleKeys) {
                        val currentDisplayed = displayedTiles[posKey]
                        if (currentDisplayed == null || currentDisplayed.lod > lod) {
                            currentDisplayed?.imageView?.let { children.remove(it) }
                            
                            if (!children.contains(imageView)) {
                                children.add(imageView)
                            }
                            displayedTiles[posKey] = DisplayedTile(imageView, lod)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                }
            }
        }
        
        pendingJobs[key] = job
    }
    
    private fun cancelInvisibleJobs() {
        val toCancel = pendingJobs.entries.filter { (key, _) ->
            squash(key.x, key.y) !in currentVisibleKeys
        }
        for ((key, job) in toCancel) {
            job.cancel()
            pendingJobs.remove(key)
        }
    }
    
    private fun cleanupDisplayed(visiblePositions: Set<Long>) {
        val toRemove = displayedTiles.keys.filter { it !in visiblePositions }
        for (key in toRemove) {
            displayedTiles.remove(key)?.imageView?.let { children.remove(it) }
        }
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
    private data class TileKey(val x: Int, val y: Int, val lod: Int)
    private data class CachedTile(val imageView: ImageView, val lod: Int)
    private data class DisplayedTile(val imageView: ImageView, val lod: Int)
    private data class GenerationTask(val tileX: Int, val tileY: Int, val lod: Int, val priority: Int)
    
    companion object {
        private const val TILE_BUFFER = 1
        private const val MAX_LOD = 3
    }
}
