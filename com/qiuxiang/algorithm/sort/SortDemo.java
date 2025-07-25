package com.qiuxiang.algorithm.sort;

import com.qiuxiang.algorithm.dichotomy.LocalMinimaDemo;

import java.util.Arrays;

public class SortDemo {
    public static void main(String[] args) {
        int[] arr = {5, 2, 9, 1, 5, 6};
        System.out.println("Original array:");
        System.out.println(java.util.Arrays.toString(arr));
        System.out.println("-----------------------------------------------------");
        localMinimumDemo(arr);
        System.out.println("-----------------------------------------------------");
        testSelectSort(arr);
        System.out.println("-----------------------------------------------------");
        testBubbleSort(arr);
        System.out.println("-----------------------------------------------------");
        testInsertSort(arr);
        System.out.println("-----------------------------------------------------");
        System.out.println("Sorted array using Bubble Sort:"+ Arrays.toString(arr));
//        dichotomySearchDemo();
        dichotomySearchDemo(arr, 5);
        System.out.println("-----------------------------------------------------");
        mergeSortDemo(arr);
    }

    private static void mergeSortDemo(int[] arr) {
        long l = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            // Create a copy of the array to sort
            MergeSortDemo.mergeSort(arr, 0, arr.length - 1);
        }
        System.out.println("Time taken for Quick Sort: " + (System.currentTimeMillis() - l) + " ms");
        System.out.println("Sorted array using Quick Sort: " + Arrays.toString(arr));
    }

    private static void localMinimumDemo(int[]arr) {
        long l = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            LocalMinimaDemo.localMinima(arr);
        }
        System.out.println("Time taken for Local Minima Search: " + (System.currentTimeMillis() - l) + " ms");
        System.out.println("Local Minimum Result: " + LocalMinimaDemo.localMinima(arr));
    }

    private static void dichotomySearchDemo(int[] arr, int target) {
        long l = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            BubbleSortDemo.bubbleSort(arr);
        }
//        System.out.println("Dichotomy Search Result: " + DichotomyDemo.dichotomy(arr, target));
        System.out.println("Time taken for Dichotomy Search: " + (System.currentTimeMillis() - l) + " ms");
    }

    private static void testBubbleSort(int[] arr) {
        long l = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            BubbleSortDemo.bubbleSort(arr);
        }
        System.out.println("Time taken for Bubble Sort: " + (System.currentTimeMillis() - l) + " ms");
        System.out.println("Sorted array using Bubble Sort:"+ Arrays.toString(arr));
    }

    private static void testSelectSort(int[] arr) {
        long l = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            SelectSortDemo.selectSort(arr);
        }
        System.out.println("Time taken for Selection Sort: " + (System.currentTimeMillis() - l) + " ms");
        System.out.println("Sorted array using Bubble Sort:"+ Arrays.toString(arr));
    }
    private static void testInsertSort(int[] arr) {
        long l = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            InsertSortDemo.insertSort(arr);
        }
        System.out.println("Time taken for Selection Sort: " + (System.currentTimeMillis() - l) + " ms");
        System.out.println("Sorted array using Bubble Sort:"+ Arrays.toString(arr));
    }
}
