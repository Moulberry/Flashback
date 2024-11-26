package com.moulberry.flashback.screen.select_replay;/*
 NaturalOrderComparator.java -- Perform 'natural order' comparisons of strings in Java.
 Copyright (C) 2003 by Pierre-Luc Paour <natorder@paour.com>

 Based on the C version by Martin Pool, of which this is more or less a straight conversion.
 Copyright (C) 2000 by Martin Pool <mbp@humbug.org.au>

 This software is provided 'as-is', without any express or implied
 warranty.  In no event will the authors be held liable for any damages
 arising from the use of this software.

 Permission is granted to anyone to use this software for any purpose,
 including commercial applications, and to alter it and redistribute it
 freely, subject to the following restrictions:

 1. The origin of this software must not be misrepresented; you must not
 claim that you wrote the original software. If you use this software
 in a product, an acknowledgment in the product documentation would be
 appreciated but is not required.
 2. Altered source versions must be plainly marked as such, and must not be
 misrepresented as being the original software.
 3. This notice may not be removed or altered from any source distribution.

===== Moulberry =====
This is an altered source version.
This message is marking it as such.
=====================
 */

import java.util.*;

public class NaturalOrderComparator implements Comparator<String> {
    public static NaturalOrderComparator INSTANCE = new NaturalOrderComparator();

    private int compareRight(String a, String b) {
        int bias = 0, ia = 0, ib = 0;

        // The longest run of digits wins. That aside, the greatest
        // value wins, but we can't know that it will until we've scanned
        // both numbers to know that they have the same magnitude, so we
        // remember it in BIAS.
        for (;; ia++, ib++) {
            char ca = charAt(a, ia);
            char cb = charAt(b, ib);

            if (!isDigit(ca) && !isDigit(cb)) {
                return bias;
            }
            if (!isDigit(ca)) {
                return -1;
            }
            if (!isDigit(cb)) {
                return +1;
            }
            if (ca == 0 && cb == 0) {
                return bias;
            }

            if (bias == 0) {
                if (ca < cb) {
                    bias = -1;
                } else if (ca > cb) {
                    bias = +1;
                }
            }
        }
    }

    public int compare(String a, String b) {
        int indexA = 0, indexB = 0;
        int numZeroesA, numZeroesB;
        char charA, charB;

        while (true) {
            // Only count the number of zeroes leading the last number compared
            numZeroesA = numZeroesB = 0;

            charA = charAt(a, indexA);
            charB = charAt(b, indexB);

            // skip over leading spaces or zeros
            while (Character.isSpaceChar(charA) || charA == '0') {
                if (charA == '0') {
                    numZeroesA++;
                } else {
                    // Only count consecutive zeroes
                    numZeroesA = 0;
                }

                charA = charAt(a, ++indexA);
            }

            while (Character.isSpaceChar(charB) || charB == '0') {
                if (charB == '0') {
                    numZeroesB++;
                } else {
                    // Only count consecutive zeroes
                    numZeroesB = 0;
                }

                charB = charAt(b, ++indexB);
            }

            if (charA == 0 && charB == 0) {
                // The strings compare the same. Perhaps the caller
                // will want to call strcmp to break the tie.
                return compareEqual(a, b, numZeroesA, numZeroesB);
            }

            // Process run of digits
            boolean isDigitA = Character.isDigit(charA);
            boolean isDigitB = Character.isDigit(charB);
            if (isDigitA && isDigitB) {
                int bias = compareRight(a.substring(indexA), b.substring(indexB));
                if (bias != 0) {
                    return bias;
                }
            }

            if (isDigitA && !isDigitB) {
                return 1;
            }
            if (isDigitB && !isDigitA) {
                return -1;
            }

            int charCompare = Character.compare(charA, charB);
            if (charCompare != 0) {
                return charCompare;
            }

            ++indexA;
            ++indexB;
        }
    }

    private static boolean isDigit(char c) {
        return Character.isDigit(c) || c == '.' || c == ',';
    }

    private static char charAt(String s, int i) {
        return i >= s.length() ? 0 : s.charAt(i);
    }

    private static int compareEqual(String a, String b, int nza, int nzb) {
        if (nza - nzb != 0) {
            return nza - nzb;
        }

        if (a.length() == b.length()) {
            return a.compareTo(b);
        }

        return a.length() - b.length();
    }

}
