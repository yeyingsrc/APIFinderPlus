package utils;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RespHashUtils {
    public static String getFaviconHash(byte[] faviconBytes) {

        String base64Favicon = Base64.getEncoder().encodeToString(faviconBytes);

        // 格式化base64字符串
        String formattedBase64Favicon = formatBase64(base64Favicon);

        // 计算格式化后base64字符串的murmurHash3值
        return String.valueOf(murmurHash3_x86_32(formattedBase64Favicon.getBytes(), 0, formattedBase64Favicon.length(), 0));
    }

    private static String formatBase64(String base64) {
        Pattern pattern = Pattern.compile(".{76}");
        Matcher matcher = pattern.matcher(base64);
        StringBuilder formattedBase64 = new StringBuilder();

        while (matcher.find()) {
            formattedBase64.append(matcher.group()).append("\n");
        }

        int remainder = base64.length() % 76;
        if (remainder > 0) {
            formattedBase64.append(base64.substring(base64.length() - remainder)).append("\n");
        }

        return formattedBase64.toString();
    }

    public static int murmurHash3_x86_32(final byte[] data, int offset, int len, final int seed) {
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;

        int h1 = seed;
        final int roundedEnd = offset + (len & 0xfffffffc); // round down to 4 byte block

        for (int i = offset; i < roundedEnd; i += 4) {
            // little endian load order
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // handle the last few bytes of the input array
        int k1 = 0;
        switch (len & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
                // fall through
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
                // fall through
            case 1:
                k1 |= (data[roundedEnd] & 0xff);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
        }

        // finalization
        h1 ^= len;

        // fmix
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }

}

