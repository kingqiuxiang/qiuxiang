package com.qiuxiang.algorithm.sort;

public class InsertSortDemo {
    /**
     * 插入排序就突出一个不稳定
     * @param arr
     */

    public static void insertSort(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            for (int j = i; j > 0; j--) {
                if (arr[j] < arr[j - 1]) {
                    swap(arr, j, j - 1);
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
