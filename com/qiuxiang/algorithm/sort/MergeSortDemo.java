package com.qiuxiang.algorithm.sort;

/**
 * this method for 归并排序
 * 归并排序的时间复杂度是O(nlogn),空间复杂度是O(n)
 * 归并排序是稳定的排序算法
 */
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
    public static int minSumCalculation(int [] arr) {
        if (arr == null || arr.length < 2) {
            return 0;
        }
        int sum=mergeMinSumSort(arr, 0, arr.length - 1);
        System.out.println("sum = " + sum);
        return sum;

}

    private static int mergeMinSumSort(int[] arr, int left, int right) {

        if (left >= right) {
            return 0;
        }
        int i = mergeMinSumSort(arr, left, (right + left) / 2);
        int i1 = mergeMinSumSort(arr, 1 + (left + right) / 2, right);
        int i2 = mergerAndSum(arr, left, (right + left) / 2, right);
        return i + i1 + i2;

    }

    private static int mergerAndSum(int[] arr, int left, int mid, int right) {
        int[] temp = new int[right - left + 1];
        int index = 0;
        int sum = 0; // 用于计算小于右边元素的左边元素的和
        int leftIndex = left;
        int rightIndex = mid + 1;
        while (leftIndex <= mid && rightIndex <= right) {
            if (arr[leftIndex] <= arr[rightIndex]) {
                //该算法 成立的原因是因为在归并排序的过程中，左边的元素已经是有序的，而右边的元素也是有序的，
                // 所以当我们遇到一个左边的元素小于右边的元素时，
                // 说明这个左边的元素会与右边的所有剩余元素形成逆序对，
                // 因此我们可以直接将这个左边元素乘以
                // 右边剩余元素的个数来计算逆序对的数量。
                // 例如：如果 leftIndex = 0, rightIndex = 3, right = 4
                // 那么 right + 1 - rightIndex = 4 - 3 = 1
                // 这意味着当前左边元素 arr[leftIndex] 会与右边的 1 个元素形成逆序对
                // 这样可以避免逐个比较左边元素和右边元素的方式，从而提高效率
                // 这里的计算逻辑是：对于每个左边的元素，如果它小于右边的元素，那么它会与右边的所有元素形成逆序对，因此
                // 需要将它乘以右边元素的个数（right + 1 - rightIndex），
                sum+=arr[leftIndex]*(right+1-rightIndex); // 计算小于右边元素的左边元素的和
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
        System.out.println("sum = " + sum);
        return sum;

    }

    public static void main(String[] args) {
        int[] arr = {1,3,4,2,5};
        int sum = minSumCalculation(arr);
        System.out.println("sum = " + sum);
    }

    }
