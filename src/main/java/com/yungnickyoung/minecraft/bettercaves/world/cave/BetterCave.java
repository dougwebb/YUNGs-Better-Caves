package com.yungnickyoung.minecraft.bettercaves.world.cave;

import com.yungnickyoung.minecraft.bettercaves.noise.NoiseTuple;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;

import java.util.Map;

public abstract class BetterCave {
    private World world;
    private long seed;

    /* ============================== Values passed in through config ============================== */
    /* ------------- Ridged Multifractal Params ------------- */
    int fractalOctaves;            // Number of ridged multifractal octaves
    float fractalGain;             // Ridged multifractal gain
    float fractalFreq;             // Ridged multifractal frequency
    int numGens;                   // Number of noise values to generate per iteration (block, sub-chunk, etc)

    /* ----------------- Turbulence Params ----------------- */
    int turbOctaves;               // Number of octaves in turbulence function
    float turbGain;                // Gain of turbulence function
    float turbFreq;                // Frequency of turbulence function
    boolean enableTurbulence;      // Set true to enable turbulence (adds performance overhead, generally not worth it)

    /* -------------- Noise Processing Params -------------- */
    float yCompression;            // Vertical cave gen compression
    float xzCompression;           // Horizontal cave gen compression
    private float yAdjustF1;       // Adjustment value for the block immediately above. Must be between 0 and 1.0
    private float yAdjustF2;       // Adjustment value for the block two blocks above. Must be between 0 and 1.0
    float noiseThreshold;          // Noise threshold for determining whether or not a block gets dug out
    boolean enableYAdjust;         // Set true to perform preprocessing on noise values, adjusting them to increase
                                   // headroom in the y direction. This is generally useful for caves (esp. Simplex),
                                   // but not really necessary for caverns

    /**
     *
     * @param world the Minecraft World
     * @param fOctaves Number of fractal octaves to use in ridged multifractal noise generation
     * @param fGain Amount of gain to use in ridged multifractal noise generation
     * @param fFreq Frequency to use in ridged multifractal noise generation
     * @param numGens Number of noise values to calculate for a given block
     * @param threshold Noise threshold to determine whether or not a given block will be dug out
     * @param tOctaves Number of octaves in turbulence function
     * @param tGain Gain of turbulence function
     * @param tFreq Frequency of turbulence function
     * @param enableTurbulence Whether or not to enable turbulence (adds performance overhead, generally not worth it).
     *                         If set to false then other turbulence params don't matter.
     * @param yComp Vertical cave gen compression. Use 1.0 for default generation
     * @param xzComp Horizontal cave gen compression. Use 1.0 for default generation
     * @param yAdj Whether or not to adjust/increase the height of caves.
     * @param yAdjF1 Adjustment value for the block immediately above. Must be between 0 and 1.0
     * @param yAdjF2 Adjustment value for the block two blocks above. Must be between 0 and 1.0
     */
    public BetterCave(World world, int fOctaves, float fGain, float fFreq, int numGens, float threshold, int tOctaves, float tGain,
                      float tFreq, boolean enableTurbulence, float yComp, float xzComp, boolean yAdj, float yAdjF1,
                      float yAdjF2) {
        this.world = world;
        this.seed = world.getSeed();
        this.fractalOctaves = fOctaves;
        this.fractalGain = fGain;
        this.fractalFreq = fFreq;
        this.numGens = numGens;
        this.noiseThreshold = threshold;
        this.turbOctaves = tOctaves;
        this.turbGain = tGain;
        this.turbFreq = tFreq;
        this.enableTurbulence = enableTurbulence;
        this.yCompression = yComp;
        this.xzCompression = xzComp;
        this.enableYAdjust = yAdj;
        this.yAdjustF1 = yAdjF1;
        this.yAdjustF2 = yAdjF2;
    }

    public World getWorld() {
        return this.world;
    }

    public long getSeed() {
        return this.seed;
    }

    /**
     * Dig out caves for the column of blocks at x-z position (chunkX*16 + localX, chunkZ*16 + localZ).
     * A given block will be calculated based on the noise value and noise threshold of this BetterCave object.
     * All of these params, including the noise function params, are attributes of this object.
     * @param chunkX The chunk's x-coordinate
     * @param chunkZ The chunk's z-coordinate
     * @param primer The ChunkPrimer for this chunk
     * @param localX the chunk-local x-coordinate of this column of blocks (0 <= localX <= 15)
     * @param localZ the chunk-local z-coordinate of this column of blocks (0 <= localZ <= 15)
     * @param bottomY The bottom y-coordinate to start calculating noise for and potentially dig out
     * @param topY The top y-coordinate to start calculating noise for and potentially dig out
     * @param maxSurfaceHeight This chunk's max surface height. Can be approximated using
     *                         BetterCaveUtil#getMaxSurfaceHeight
     * @param minSurfaceHeight This chunk's min surface height. Can be approximated using
     *                         BetterCaveUtil#getMinSurfaceHeight
     */
    public abstract void generateColumn(int chunkX, int chunkZ, ChunkPrimer primer, int localX, int localZ, int bottomY,
                      int topY, int maxSurfaceHeight, int minSurfaceHeight);

    /**
     * Preprocessing performed on a column of noise to adjust its values before comparing them to the threshold.
     * This function adjusts the noise value of blocks based on the noise values of blocks below and nearby.
     * This has the effect of raising the ceilings of caves, giving the player more headroom.
     * Big shoutouts to the guys behind Worley's Caves for this great idea.
     * @param noises The column of noises as a map, mapping the y-coordinate of a block to its NoiseTuple
     * @param topY Top y-coordinate of the noise column
     * @param bottomY Bottom y-coodinate of the noise column
     * @param numGens Number of noise values to create per block. This is equal to the number of floats held
     *                in each NoiseTuple for each block in the noise column.
     */
    void preprocessCaveNoiseCol(Map<Integer, NoiseTuple> noises, int topY, int bottomY, int numGens) {
        /* Adjust simplex noise values based on blocks above in order to give the player more headroom */
        for (int realY = topY; realY >= bottomY; realY--) {
            NoiseTuple sBlockNoise = noises.get(realY);
            float avgSNoise = 0;

            for (float noise : sBlockNoise.getNoiseValues())
                avgSNoise += noise;

            avgSNoise /= sBlockNoise.size();

            if (avgSNoise > this.noiseThreshold) {
                /* Adjust noise values of blocks above to give the player more head room */
                float f1 = this.yAdjustF1;
                float f2 = this.yAdjustF2;

                if (realY < topY) {
                    NoiseTuple tupleAbove = noises.get(realY + 1);
                    for (int i = 0; i < numGens; i++)
                        tupleAbove.set(i, ((1 - f1) * tupleAbove.get(i)) + (f1 * sBlockNoise.get(i)));
                }

                if (realY < topY - 1) {
                    NoiseTuple tupleTwoAbove = noises.get(realY + 2);
                    for (int i = 0; i < numGens; i++)
                        tupleTwoAbove.set(i, ((1 - f2) * tupleTwoAbove.get(i)) + (f2 * sBlockNoise.get(i)));
                }
            }
        }
    }
}