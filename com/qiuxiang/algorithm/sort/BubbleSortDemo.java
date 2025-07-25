package com.qiuxiang.algorithm.sort;

public class BubbleSortDemo {


    public static void bubbleSort(int[] sourceArray) {
        if (sourceArray == null || sourceArray.length < 2) {
            return;
        }
        for (int i = sourceArray.length - 1; i >= 0; i--) {
            for (int j = 0; j < i; j++) {
                if (sourceArray[j] > sourceArray[j + 1]) {
                    swap(sourceArray, j, j + 1);
                }
            }
        }
    }


    private static void swap(int[] randomArray, int i, int minIndex) {
        if (minIndex != i && randomArray[minIndex] != randomArray[i]) {
            randomArray[i] = randomArray[i] ^ randomArray[minIndex];
            randomArray[minIndex] = randomArray[i] ^ randomArray[minIndex];
            randomArray[i] = randomArray[i] ^ randomArray[minIndex];
        }
    }

}
