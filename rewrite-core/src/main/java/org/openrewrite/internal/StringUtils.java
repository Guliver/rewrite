/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.internal;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public class StringUtils {
    public static String trimIndent(String text) {
        int indentLevel = indentLevel(text);

        StringBuilder trimmed = new StringBuilder();
        int[] charArray = text.stripTrailing().chars().dropWhile(c -> c == '\n' || c == '\r').toArray();
        for (int i = 0; i < charArray.length; i++) {
            boolean nonWhitespaceEncountered = false;
            int j = i;
            for (; j < charArray.length; j++) {
                int c = charArray[j];
                if (j - i >= indentLevel || (nonWhitespaceEncountered |= !Character.isWhitespace(c))) {
                    trimmed.append((char) c);
                }
                if (c == '\r' || c == '\n') {
                    break;
                }
            }
            i = j;
        }

        return trimmed.toString();
    }

    static int indentLevel(String text) {
        Stream<String> lines = text.stripTrailing().lines();
        SortedMap<Integer, Long> indentFrequencies = lines
                .dropWhile(String::isEmpty)
                .map(l -> (int) l.chars().takeWhile(Character::isWhitespace).count())
                .collect(groupingBy(identity(), TreeMap::new, counting()));
        return mostCommonIndent(indentFrequencies);
    }

    public static int mostCommonIndent(SortedMap<Integer, Long> indentFrequencies) {
        // the frequency with which each indent level is an integral divisor of longer indent levels
        SortedMap<Integer, Integer> indentFrequencyAsDivisors = new TreeMap<>();
        for (Map.Entry<Integer, Long> indentFrequency : indentFrequencies.entrySet()) {
            int indent = indentFrequency.getKey();
            int freq;
            switch(indent) {
                case 0:
                    freq = indentFrequency.getValue().intValue();
                    break;
                case 1:
                    // gcd(1, N) == 1, so we can avoid the test for this case
                    freq = (int) indentFrequencies.tailMap(indent).values().stream().mapToLong(l -> l).sum();
                    break;
                default:
                    freq = (int) indentFrequencies.tailMap(indent).entrySet().stream()
                            .filter(inF -> gcd(inF.getKey(), indent) != 0)
                            .mapToLong(Map.Entry::getValue)
                            .sum();
            }

            indentFrequencyAsDivisors.put(indent, freq);
        }

        if(indentFrequencies.getOrDefault(0, 0L) > 1) {
            return 0;
        }

        return indentFrequencyAsDivisors.entrySet().stream()
                .max((e1, e2) -> {
                    var valCompare = e1.getValue().compareTo(e2.getValue());
                    return valCompare != 0 ?
                            valCompare :
                            // take the smallest indent otherwise, unless it would be zero
                            e1.getKey() == 0 ? -1 : e2.getKey().compareTo(e1.getKey());
                })
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    static int gcd(int n1, int n2) {
        return n2 == 0 ? n1 : gcd(n2, n1 % n2);
    }
}