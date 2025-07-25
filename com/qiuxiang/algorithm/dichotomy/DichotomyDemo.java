package com.qiuxiang.algorithm.dichotomy;

public class DichotomyDemo {

    /**
     * Dichotomy search algorithm ,前提是要有序才行
     * @param arr
     * @param target
     * @return
     */

    public static boolean dichotomy(int[] arr, int target) {
        int left = 0;
        int right = arr.length - 1;
        int mid = 0;
        while (left < right) {
            mid = left + (right - left) >> 1; // Avoid overflow
            if (arr[mid] == target) {
                return true;
            }
            if (arr[mid] < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return arr[left] == target;
    }
}