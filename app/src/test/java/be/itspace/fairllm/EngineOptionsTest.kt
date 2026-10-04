package be.itspace.fairllm

import org.junit.Assert.*
import org.junit.Test

class EngineOptionsTest {
    @Test fun detectedGpuIsUsedInAutomaticMode() {
        val options = EngineOptions.choose("auto", "Adreno 810", 24, 8)
        assertTrue(options.useGpu)
        val args = options.arguments(MODEL_PRESETS[1])
        assertEquals("24", args[args.indexOf("-ngl") + 1])
        assertEquals("4", args[args.indexOf("-t") + 1])
        assertEquals("64", args[args.indexOf("-ub") + 1])
        assertEquals("off", args[args.indexOf("-fa") + 1])
        assertEquals("f32", args[args.indexOf("-ctk") + 1])
        assertEquals("f32", args[args.indexOf("-ctv") + 1])
    }
    @Test fun unavailableGpuUsesCpu() {
        assertFalse(EngineOptions.choose("auto", "", 24, 8).useGpu)
    }
    @Test fun cpuModeDoesNotOffloadEvenWhenGpuExists() {
        val options = EngineOptions.choose("cpu", "Adreno", 99, 2)
        assertFalse(options.useGpu)
        val args = options.arguments(MODEL_PRESETS[0])
        assertEquals("0", args[args.indexOf("-ngl") + 1])
        assertEquals("auto", args[args.indexOf("-fa") + 1])
        assertEquals("f16", args[args.indexOf("-ctk") + 1])
        assertEquals(2, options.threads)
    }
    @Test fun threadAndLayerCountsStayWithinBounds() {
        assertEquals(1, EngineOptions.choose("auto", "GPU", -1, 0).gpuLayers)
        assertEquals(1, EngineOptions.choose("auto", "GPU", -1, 0).threads)
        assertEquals(99, EngineOptions.choose("auto", "GPU", 999, 999).gpuLayers)
    }
}
