package com.fangsu.utils.nte;

public class IntegerArraySerializer {
    public static String serialize(Integer[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == null) {
                sb.append("null");
            } else {
                sb.append(arr[i]);
            }
            if (i!= arr.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    public static Integer[] deserialize(String str) {
        String[] arr = str.split(",");
        Integer[] result = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals("null")) {
                result[i] = null;
            } else {
                result[i] = Integer.parseInt(arr[i]);
            }
        }
        return result;
    }
}