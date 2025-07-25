package com.qiuxiang.algorithm.sort;


import java.util.Random;

/**
 * O(n log n) time complexity
 * O(n) space complexity
 * This implementation uses the Dutch National Flag partitioning algorithm
 * which is a three-way partitioning algorithm that divides the array into three parts:
 * 1. values less than the pivot
 * 2. values equal to the pivot
 * 3. values greater than the pivot
 * This algorithm is useful when there are many duplicate values in the array
 */
public class QuicklySortDemo {


    public static void quicklySort(int[] randomArray) {

        if (randomArray == null || randomArray.length < 2) {
            return;
        }
        sort2(randomArray, 0, randomArray.length - 1);
    }

    private static void sort(int[] randomArray, int left, int right) {
    if (left >= right) {
        return;
    }
        int[] index = partition2(randomArray, left, right);
        sort(randomArray, left, index[0] - 1);
        sort(randomArray, index[1]+1, right);

    }

    /**
     * random array sort by DNF partition
     * DNF partition is a three-way partitioning algorithm that divides the array into three parts
     * 1. values less than the pivot
     * 2. values equal to the pivot
     * 3. values greater than the pivot
     * This algorithm is useful when there are many duplicate values in the array
     * It is also known as Dutch National Flag partitioning
     * @param randomArray
     * @param left
     * @param right
     */
    private static void sort2(int[] randomArray, int left, int right) {
        if (left >= right) {
            return;
        }
        int[] index = partition2(randomArray, left, right);
        sort(randomArray, left, index[0] - 1);
        sort(randomArray, index[1]+1, right);

    }


    private static int partition(int[] arr, int L, int R) {
        int pivot = arr[R];

        int low = L; // low pointer for values less than pivot
        int mid = L; // mid pointer for values equal to pivot
        // we use low pointer to track the position of the last value less than the pivot
        int high = R;
        while (mid <= high) {
            if (pivot > arr[mid]) {
                //step1: if the value less than the pivot, still in there but if it large than the pivot, it should be in the left side
                swap(arr, low++, mid++);
            } else if (pivot < arr[mid]) {
                //step 2: if the value larger than the pivot, it should be in the right side
                // so we swap it with the high pointer and move the high pointer to the left
                swap(arr, mid, high--);
            } else {
                //step 3: if the value equals to the pivot, it should be in the middle
                // so we just move the mid pointer
                // this is the key point of DNF partition, it can make sure the value equals
                // to the pivot will be in the middle part
                mid++;
            }
        }
        return low;
    }


    private static int[] partition2(int[] arr, int L, int R) {
        int pivot = arr[new Random().nextInt(R - L) + L];

        int low = L; // low pointer for values less than pivot
        int mid = L; // mid pointer for values equal to pivot
        // we use low pointer to track the position of the last value less than the pivot
        int high = R;
        while (mid <= high) {
            if (pivot > arr[mid]) {
                //step1: if the value less than the pivot, still in there but if it large than the pivot, it should be in the left side
                swap(arr, low++, mid++);
            } else if (pivot < arr[mid]) {
                //step 2: if the value larger than the pivot, it should be in the right side
                // so we swap it with the high pointer and move the high pointer to the left
                swap(arr, mid, high--);
            } else {
                //step 3: if the value equals to the pivot, it should be in the middle
                // so we just move the mid pointer
                // this is the key point of DNF partition, it can make sure the value equals
                // to the pivot will be in the middle part
                mid++;
            }
        }
        return new int[]{low, high};
    }
    private static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;

    }

    public static void main(String[] args) {
        int[] arr = {3, 6, 8, 10, 1, 2, 1};
        System.out.println("Before sorting: ");
        for (int num : arr) {
            System.out.print(num + " ");
        }
        System.out.println();

        quicklySort(arr);

        System.out.println("After sorting: ");
        for (int num : arr) {
            System.out.print(num + " ");
        }
        System.out.println();
    }

}
