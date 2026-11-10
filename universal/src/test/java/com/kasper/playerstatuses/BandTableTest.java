package com.kasper.playerstatuses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BandTableTest {

    @Test
    void standardRangesAndPreReleases() {
        assertTrue(BandTable.matches("[1.21,1.21.2)", "1.21"));
        assertTrue(BandTable.matches("[1.21,1.21.2)", "1.21.0"));
        assertTrue(BandTable.matches("[1.21,1.21.2)", "1.21.1"));
        assertFalse(BandTable.matches("[1.21,1.21.2)", "1.21.2"));
        assertFalse(BandTable.matches("[1.21,1.21.2)", "1.21.2-rc1"),
                "pre-release of exclusive upper bound belongs to the next band");
        assertTrue(BandTable.matches("[1.21.2,1.21.5)", "1.21.2-rc1"),
                "pre-release of inclusive lower bound belongs inside the band");
        assertTrue(BandTable.matches("[21.11,)", "21.11.0-beta"));
        assertTrue(BandTable.matches("[21.11,)", "21.11.38-beta"));
        assertFalse(BandTable.matches("[21.11,)", "21.10.64"));
    }

    @Test
    void bareLowerBoundsAndMultipleIntervals() {
        assertTrue(BandTable.matches("47.2.0", "47.2.0"));
        assertTrue(BandTable.matches("47.2.0", "47.3.1"));
        assertFalse(BandTable.matches("47.2.0", "47.1.9"));
        assertTrue(BandTable.matches("[1.19,1.19.3), [1.19.4]", "1.19.2"));
        assertFalse(BandTable.matches("[1.19,1.19.3), [1.19.4]", "1.19.3"));
        assertTrue(BandTable.matches("[1.19,1.19.3), [1.19.4]", "1.19.4"));
    }

    @Test
    void qualifierAndNumericOrdering() {
        assertTrue(BandTable.compare("1.21.2-snapshot", "1.21.2-alpha1") < 0);
        assertTrue(BandTable.compare("1.21.2-alpha2", "1.21.2-beta1") < 0);
        assertTrue(BandTable.compare("1.21.2-beta2", "1.21.2-pre1") < 0);
        assertTrue(BandTable.compare("1.21.2-pre4", "1.21.2-rc1") < 0);
        assertTrue(BandTable.compare("1.21.2-rc2", "1.21.2-rc10") < 0);
        assertTrue(BandTable.compare("1.21.2-rc10", "1.21.2") < 0);
        assertTrue(BandTable.compare("1.21", "1.21-2.5.0") < 0);
        assertEquals(0, BandTable.compare("1.21.0+build.5", "1.21"));
    }
}
