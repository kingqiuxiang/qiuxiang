package com.qiuxiang.algorithm.sort;

public class MergeSortDemo {


    public static void mergeSort(int[] arr, int left, int right) {

        if (left >= right) {
            return;
        }
        mergeSort(arr, left, (right + left) / 2);
        mergeSort(arr, 1 + (left + right) / 2, right);
        merger(arr, left, (right + left) / 2, right);


    }

    private static void merger(int[] arr, int left, int mid, int right) {
        int[] temp = new int[right - left + 1];
        int index = 0;
        int leftIndex = left;
        int rightIndex = mid + 1;
        while (leftIndex <= mid && rightIndex <= right) {
            if (arr[leftIndex] <= arr[rightIndex]) {
                temp[index++] = arr[leftIndex++];
            } else {
                temp[index++] = arr[rightIndex++];
            }
        }
        while (leftIndex <= mid) {

            temp[index++] = arr[leftIndex++];
        }

        while (rightIndex <= right) {
            temp[index++] = arr[rightIndex++];
        }
        for (int i : temp) {
            arr[left++] = i;
        }

    }

}
