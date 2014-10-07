/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.util;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.util.ReflectionUtils;

public class CachingPasswordEncoderTest  {

    CachingPasswordEncoder cachingPasswordEncoder;

    @Before
    public void setUp() throws Exception {
        cachingPasswordEncoder = new CachingPasswordEncoder();
        cachingPasswordEncoder.setPasswordEncoder(new BCryptPasswordEncoder());
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testSetPasswordEncoder() throws Exception {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        cachingPasswordEncoder.setPasswordEncoder(encoder);
        assertSame(encoder, cachingPasswordEncoder.getPasswordEncoder());
    }


    @Test
    public void testEncode() throws Exception {
        String password = new RandomValueStringGenerator().generate();
        String encode1 = cachingPasswordEncoder.encode(password);
        String encode2 = cachingPasswordEncoder.getPasswordEncoder().encode(password);
        assertFalse(encode1.equals(encode2));
        assertTrue(cachingPasswordEncoder.getPasswordEncoder().matches(password, encode1));
        assertTrue(cachingPasswordEncoder.getPasswordEncoder().matches(password, encode2));
        assertTrue(cachingPasswordEncoder.matches(password, encode1));
        assertTrue(cachingPasswordEncoder.matches(password, encode2));
    }

    @Test
    public void testMatches() throws Exception {
        String password = new RandomValueStringGenerator().generate();
        cachingPasswordEncoder.encode(password);
        String encoded = cachingPasswordEncoder.encode(password);
        int iterations = 20;
        for (int i=0; i<iterations; i++) {
            assertTrue(cachingPasswordEncoder.getPasswordEncoder().matches(password, encoded));
            assertTrue(cachingPasswordEncoder.matches(password, encoded));
        }
    }

    @Test
    public void testNotMatches() throws Exception {
        String password = new RandomValueStringGenerator().generate();
        cachingPasswordEncoder.encode(password);
        String encoded = cachingPasswordEncoder.encode(password);
        password = new RandomValueStringGenerator().generate();
        int iterations = 20;
        for (int i=0; i<iterations; i++) {
            assertFalse(cachingPasswordEncoder.getPasswordEncoder().matches(password, encoded));
            assertFalse(cachingPasswordEncoder.matches(password, encoded));
        }
    }

    @Test
    public void testMatchesSpeedTest() throws Exception {
        int iterations = 100;

        String password = new RandomValueStringGenerator().generate();
        String encodedBcrypt = cachingPasswordEncoder.encode(password);
        long nanoStart = System.nanoTime();
        for (int i=0; i<iterations; i++) {
            assertTrue(cachingPasswordEncoder.getPasswordEncoder().matches(password, encodedBcrypt));
        }
        long nanoStop = System.nanoTime();
        long bcryptTime = nanoStop - nanoStart;
        nanoStart = System.nanoTime();
        for (int i=0; i<iterations; i++) {
            assertTrue(cachingPasswordEncoder.matches(password, encodedBcrypt));
        }
        nanoStop = System.nanoTime();
        long cacheTime = nanoStop - nanoStart;
        //assert that the cache is at least 10 times faster
        assertTrue(bcryptTime > (10 * cacheTime));
        System.out.println("CachingPasswordEncoder - Bcrypt Time:"+((double)bcryptTime / 1000000000.0) + " sec. Cache Time:"+((double)cacheTime / 1000000000.0)+" sec.");
    }

    @Test
    public void testEnsureNoMemoryLeak() {
        int maxkeys = 10;
        int maxpasswords = 4;
        cachingPasswordEncoder.setMaxKeys(maxkeys);
        assertEquals(maxkeys, cachingPasswordEncoder.getMaxKeys());
        cachingPasswordEncoder.setMaxEncodedPasswords(4);
        assertEquals(maxpasswords, cachingPasswordEncoder.getMaxEncodedPasswords());
        assertEquals(0, cachingPasswordEncoder.getNumberOfKeys());
        for (int i=0; i<cachingPasswordEncoder.getMaxKeys(); i++) {
            String password = new RandomValueStringGenerator().generate();
            for (int j=0; j<cachingPasswordEncoder.getMaxEncodedPasswords(); j++) {
                String encoded = cachingPasswordEncoder.encode(password);
                assertTrue(cachingPasswordEncoder.matches(password, encoded));
            }
        }
        assertEquals(maxkeys, cachingPasswordEncoder.getNumberOfKeys());
        String password = new RandomValueStringGenerator().generate();
        String encoded = cachingPasswordEncoder.encode(password);
        assertTrue(cachingPasswordEncoder.matches(password, encoded));
        //overflow happened
        assertEquals(0, cachingPasswordEncoder.getNumberOfKeys());

        for (int j=0; j<cachingPasswordEncoder.getMaxEncodedPasswords(); j++) {
            encoded = cachingPasswordEncoder.encode(password);
            assertTrue(cachingPasswordEncoder.matches(password, encoded));
        }

        Field field = ReflectionUtils.findField(cachingPasswordEncoder.getClass(), "cache");
        field.setAccessible(true);
        ConcurrentHashMap<CharSequence, Set<String>> cache = (ConcurrentHashMap<CharSequence, Set<String>>)ReflectionUtils.getField(
            field,
            cachingPasswordEncoder
        );
        assertNotNull(cache);
        Set<String> passwords = cache.get(cachingPasswordEncoder.cacheEncode(password));
        assertNotNull(passwords);
        assertEquals(maxpasswords, passwords.size());
        cachingPasswordEncoder.matches(password, cachingPasswordEncoder.encode(password));
        assertEquals(0, passwords.size());
    }
}