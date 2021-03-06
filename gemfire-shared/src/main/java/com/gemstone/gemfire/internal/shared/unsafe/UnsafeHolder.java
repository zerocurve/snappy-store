/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.gemstone.gemfire.internal.shared.unsafe;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import com.gemstone.gemfire.internal.shared.ChannelBufferFramedInputStream;
import com.gemstone.gemfire.internal.shared.ChannelBufferFramedOutputStream;
import com.gemstone.gemfire.internal.shared.ChannelBufferInputStream;
import com.gemstone.gemfire.internal.shared.ChannelBufferOutputStream;
import com.gemstone.gemfire.internal.shared.InputStreamChannel;
import com.gemstone.gemfire.internal.shared.OutputStreamChannel;
import org.apache.spark.unsafe.Platform;
import sun.misc.Cleaner;

/**
 * Holder for static sun.misc.Unsafe instance and some convenience methods. Use
 * other methods only if {@link UnsafeHolder#hasUnsafe()} returns true;
 *
 * @author swale
 * @since gfxd 1.1
 */
public abstract class UnsafeHolder {

  private static final class Wrapper {

    static final sun.misc.Unsafe unsafe;
    static final Constructor<?> directBufferConstructor;
    static final Field directBufferCleanerField;

    static {
      sun.misc.Unsafe v;
      Constructor<?> dbConstructor;
      Field dbCleanerField;
      // try using "theUnsafe" field
      try {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        v = (sun.misc.Unsafe)field.get(null);

        Class<?> cls = Class.forName("java.nio.DirectByteBuffer");
        dbConstructor = cls.getDeclaredConstructor(
            Long.TYPE, Integer.TYPE);
        dbConstructor.setAccessible(true);
        dbCleanerField = cls.getDeclaredField("cleaner");
        dbCleanerField.setAccessible(true);
      } catch (LinkageError le) {
        throw le;
      } catch (Throwable t) {
        throw new ExceptionInInitializerError(t);
      }
      if (v == null) {
        throw new ExceptionInInitializerError("theUnsafe not found");
      }
      unsafe = v;
      directBufferConstructor = dbConstructor;
      directBufferCleanerField = dbCleanerField;
    }

    static void init() {
    }
  }

  private static final boolean hasUnsafe;

  static {
    boolean v;
    try {
      Wrapper.init();
      v = true;
    } catch (LinkageError le) {
      le.printStackTrace();
      v = false;
    }
    hasUnsafe = v;
  }

  private UnsafeHolder() {
    // no instance
  }

  public static boolean hasUnsafe() {
    return hasUnsafe;
  }

  public static ByteBuffer allocateDirectBuffer(int size) {
    return allocateDirectBuffer(Platform.allocateMemory(size), size);
  }

  public static ByteBuffer allocateDirectBuffer(final long address, int size) {
    try {
      ByteBuffer buffer = (ByteBuffer)Wrapper.directBufferConstructor
          .newInstance(address, size);
      Cleaner cleaner = Cleaner.create(buffer, new Runnable() {
        @Override
        public void run() {
          Platform.freeMemory(address);
        }
      });
      Wrapper.directBufferCleanerField.set(buffer, cleaner);
      return buffer;
    } catch (Exception e) {
      Platform.throwException(e);
      throw new IllegalStateException("unreachable");
    }
  }

  public static long getDirectBufferAddress(Buffer buffer) {
    return ((sun.nio.ch.DirectBuffer)buffer).address();
  }

  public static void releaseIfDirectBuffer(Buffer buffer) {
    if (buffer != null && buffer.isDirect()) {
      releaseDirectBuffer(buffer);
    }
  }

  public static void releaseDirectBuffer(Buffer buffer) {
    sun.misc.Cleaner cleaner = ((sun.nio.ch.DirectBuffer)buffer).cleaner();
    if (cleaner != null) {
      cleaner.clean();
      cleaner.clear();
    }
  }

  public static sun.misc.Unsafe getUnsafe() {
    return Wrapper.unsafe;
  }

  @SuppressWarnings("resource")
  public static InputStreamChannel newChannelBufferInputStream(
      ReadableByteChannel channel, int bufferSize) throws IOException {
    return (hasUnsafe
        ? new ChannelBufferUnsafeInputStream(channel, bufferSize)
        : new ChannelBufferInputStream(channel, bufferSize));
  }

  @SuppressWarnings("resource")
  public static OutputStreamChannel newChannelBufferOutputStream(
      WritableByteChannel channel, int bufferSize) throws IOException {
    return (hasUnsafe
        ? new ChannelBufferUnsafeOutputStream(channel, bufferSize)
        : new ChannelBufferOutputStream(channel, bufferSize));
  }

  @SuppressWarnings("resource")
  public static InputStreamChannel newChannelBufferFramedInputStream(
      ReadableByteChannel channel, int bufferSize) throws IOException {
    return (hasUnsafe
        ? new ChannelBufferUnsafeFramedInputStream(channel, bufferSize)
        : new ChannelBufferFramedInputStream(channel, bufferSize));
  }

  @SuppressWarnings("resource")
  public static OutputStreamChannel newChannelBufferFramedOutputStream(
      WritableByteChannel channel, int bufferSize) throws IOException {
    return (hasUnsafe
        ? new ChannelBufferUnsafeFramedOutputStream(channel, bufferSize)
        : new ChannelBufferFramedOutputStream(channel, bufferSize));
  }

  /**
   * Checks that the range described by {@code offset} and {@code size}
   * doesn't exceed {@code arrayLength}.
   */
  public static void checkBounds(int arrayLength, int offset, int len) {
    if ((offset | len) < 0 || offset > arrayLength ||
        arrayLength - offset < len) {
      throw new ArrayIndexOutOfBoundsException("Array index out of range: " +
          "length=" + arrayLength + " offset=" + offset + " length=" + len);
    }
  }
}
