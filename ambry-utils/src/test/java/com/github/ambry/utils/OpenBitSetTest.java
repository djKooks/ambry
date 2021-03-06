/**
 * Copyright 2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.github.ambry.utils;

import org.junit.Assert;
import org.junit.Test;

import static com.github.ambry.utils.FilterTestHelper.*;
import static org.junit.Assert.*;


/**
 * OpenBitSet Test
 */
public class OpenBitSetTest {
  @Test
  public void testOpenBitSetTest() {
    OpenBitSet bitSet = new OpenBitSet(1000, BLOOM_FILTER_MAX_PAGE_COUNT);
    bitSet.set(0);
    bitSet.set(100);
    Assert.assertTrue(bitSet.get(0));
    Assert.assertTrue(bitSet.get(100));
    Assert.assertFalse(bitSet.get(1));
    bitSet.clear(0);
    Assert.assertFalse(bitSet.get(0));
    Assert.assertEquals(bitSet.capacity(), 1024);
    Assert.assertEquals(bitSet.size(), 1024);
    Assert.assertEquals(bitSet.length(), 1024);
    Assert.assertFalse(bitSet.isEmpty());
    Assert.assertEquals(bitSet.cardinality(), 1);
    OpenBitSet bitSet2 = new OpenBitSet(1000, BLOOM_FILTER_MAX_PAGE_COUNT);
    bitSet2.set(100);
    bitSet2.set(1);
    bitSet2.and(bitSet);
    Assert.assertTrue(bitSet2.get(100));
    Assert.assertFalse(bitSet2.get(1));
    bitSet2.intersect(bitSet);
    Assert.assertTrue(bitSet2.get(100));
    OpenBitSet bitSet3 = new OpenBitSet(1000, BLOOM_FILTER_MAX_PAGE_COUNT);
    bitSet3.set(100);
    Assert.assertEquals(bitSet2, bitSet3);
    bitSet3.set(101);
    bitSet3.set(102);
    bitSet3.set(103);
    bitSet3.clear(100, 104);
    Assert.assertFalse(bitSet3.get(100));
    Assert.assertFalse(bitSet3.get(101));
    Assert.assertFalse(bitSet3.get(102));
    Assert.assertFalse(bitSet3.get(103));
  }

  @Test
  public void testInvalidOpenBitSet() {
    try {
      new OpenBitSet(-1, BLOOM_FILTER_MAX_PAGE_COUNT);
      fail("Negative numBits should fail");
    } catch (IllegalArgumentException e) {
      // expected
    }
    try {
      new OpenBitSet(Integer.MAX_VALUE, BLOOM_FILTER_MAX_PAGE_COUNT);
      fail("Too large page count should fail");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
}
