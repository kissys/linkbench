/*
 * Copyright 2012, Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.LinkBench.generators;

import java.util.Properties;
import java.util.Random;

import com.facebook.LinkBench.Config;
import com.facebook.LinkBench.ConfigUtil;
import com.facebook.LinkBench.LinkBenchConfigError;

/**
 * A simple data generator where the same sequences of bytes, or "motifs" occur
 * multiple times.  This is designed to emulate one particular property of real
 * data that is exploited by compression algorithms.  Typically a short sequence
 * of data generated by this generator will not be very compressible on its own, 
 * as no motifs will recur, but if multiple output strings are concatenated 
 * together then the same motifs will recur repeatedly and the data will be compressible.  
 * 
 * The motif data generator has a buffer of "shared" motifs, which reoccur
 * frequently in the output of the generator
 * 
 * The data generator generates bytes from within the range of values [min, max).
 * There is an additional parameter, which is called uniqueness for lack of a
 * better name.  The generator fills a buffer with data in chunks.  A chunk
 * is either generated as random new bytes, or is drawn from the "motifs",
 *  
 * The uniqueness parameter controls the proportion of new chunks versus duplicated
 * motifs.  It is a probability between 0.0 and 1.0. It can also be seen as the expected
 * percentage of bytes are generated from scratch.
 * 
 * Control how often motifs appear in data 
 * uniqueness = 0.0: all data drawn from motifs
 * uniqueness 1.0: completely independent bytes
 */
public class MotifDataGenerator implements DataGenerator {
  private static final int MAX_CHUNK_SIZE = 128;

  public static final int DEFAULT_MOTIF_BUFFER_SIZE = 512;
  

  /** Lowest byte to appear in output */  
  private int start;
  /** Number of distinct bytes to appear in output */
  private int range;
  /** percentage of data drawn from motifs */
  private double uniqueness;
  

  /** 
   * Buffer with a sequence of random bytes that are 
   * pasted into output.  Starts off null, initialized
   * on demand.
   */
  private byte motifs[];
  /** Size of motif buffer */
  private int motifBytes;
  

  public MotifDataGenerator() {
    start = '\0';
    range = 1;
    uniqueness = 0.0;
  }
  
  /**
   * Generate characters from start to end (inclusive both ends)
   * @param start
   * @param end
   */
  public void init(int start, int end, double uniqueness) {
    init(start, end, uniqueness, DEFAULT_MOTIF_BUFFER_SIZE);
  }
  
  public void init(int start, int end, double uniqueness, int motifBytes) {
    if (start < 0 || start >= 256) {
      throw new LinkBenchConfigError("start " + start +
                                     " out of range [0,255]");
    }
    if (end < 0 || end >= 256) {
      throw new LinkBenchConfigError("endbyte " + end +
                                     " out of range [0,255]");
    }

    if (start >= end) {
      throw new LinkBenchConfigError("startByte " + start 
                                   + " >= endByte " + end);
    }
    this.start = (byte)start;
    this.range = end - start + 1;
    this.uniqueness = uniqueness;
    this.motifBytes = motifBytes;
    this.motifs = null;
  }
  
  @Override
  public void init(Properties props, String keyPrefix) {
    int startByte = ConfigUtil.getInt(props, keyPrefix +
                                     Config.UNIFORM_GEN_STARTBYTE);
    int endByte = ConfigUtil.getInt(props, keyPrefix +
                                     Config.UNIFORM_GEN_ENDBYTE);
    double uniqueness = ConfigUtil.getDouble(props, keyPrefix +
                                     Config.MOTIF_GEN_UNIQUENESS);
    if (props.contains(keyPrefix + Config.MOTIF_GEN_LENGTH)) {
      int motifBytes = ConfigUtil.getInt(props, keyPrefix 
                               + Config.MOTIF_GEN_LENGTH);
      init(startByte, endByte, uniqueness, motifBytes);
    } else {
      init(startByte, endByte, uniqueness);
    }
  }

  /**
   * Give an upper bound for the compression ratio for the algorithm 
   * @return number between 0.0 and 1.0 - 0.0 is perfectly compressible, 
   *         1.0 is incompressible
   */
  public double estMaxCompression() {
    // Avg bytes required to represent each character (uniformly distributed)
    double charCompression = range / (double) 255;
    // random data shouldn't have any inter-character correlations that can
    // be compressed.  Upper bound derived by assuming motif is completely 
    // compressible  
    return charCompression * uniqueness;
  }
  
  @Override
  public byte[] fill(Random rng, byte[] data) {
    // Fill motifs now so that we can use rng
    if (motifs == null) {
      motifs = new byte[motifBytes];
      for (int i = 0; i < motifs.length; i++) {
        motifs[i] = (byte) (start + rng.nextInt(range));
      }
    }
    
    int n = data.length;
    int chunk = Math.min(MAX_CHUNK_SIZE, motifBytes);
    
    for (int i = 0; i < n; i += chunk) {
      if (rng.nextDouble() < uniqueness) {
        int chunkEnd = Math.min(n, i + chunk);
        // New sequence of unique bytes
        for (int j = i; j < chunkEnd; j++) {
          data[j] = (byte) (start + rng.nextInt(range));
        }
      } else {
        int thisChunk = Math.min(chunk, n - i);
        int k = rng.nextInt(motifBytes - thisChunk + 1);
        // Copy previous sequence of bytes
        System.arraycopy(motifs, k, data, i, thisChunk);
      }
    }
    return data;
  }

}
