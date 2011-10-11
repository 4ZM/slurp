/**
 * Copyright (c) 2011 Anders Sundman <anders@4zm.org>
 * 
 * This file is part of SLURP.
 * 
 * SLURP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * SLURP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with SLURP.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sparvnastet.slurp;

public class DataFormater {
    public static byte[] fromHexString(final String encoded) {
        if ((encoded.length() % 2) != 0)
            throw new IllegalArgumentException("Input string must contain an even number of characters");

        final byte result[] = new byte[encoded.length() / 2];
        final char enc[] = encoded.toCharArray();
        for (int i = 0; i < enc.length; i += 2) {
            StringBuilder curr = new StringBuilder(2);
            curr.append(enc[i]).append(enc[i + 1]);
            result[i / 2] = (byte) Integer.parseInt(curr.toString(), 16);
        }
        return result;
    }

    public static String byteToHexString(byte b) {
        String hex = Integer.toHexString(b & 0xff);
        return (b & 0xff) < 0x10 ? "0" + hex : hex;
    }

    public static String bytesToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append(byteToHexString(bytes[0]));
        for (int i = 1; i < bytes.length; ++i) {
            sb.append(" ");
            sb.append(byteToHexString(bytes[i]));
        }
        return sb.toString();
    }
}