package com.qiuxiang.algorithm.dichotomy;

public class LocalMminimaDemo {

    public static boolean localMinima(int[] arr) {
        if (index == 0) {
            return arr[index] < arr[index + 1];
        } else if (index == arr.length - 1) {
            return arr[index] < arr[index - 1];
        } else {
            return arr[index] < arr[index - 1] && arr[index] < arr[index + 1];
        }
    }


}
