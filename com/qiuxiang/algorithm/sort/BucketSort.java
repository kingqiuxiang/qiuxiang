package com.qiuxiang.algorithm.sort;

import java.util.LinkedList;
import java.util.Queue;

public class BucketSort {
    /**
     * 桶排序的核心思想是将数据分到有限数量的桶里，每个桶再分别排序
     * 最后再将各个桶中的数据合并起来
     * 桶排序适用于数据分布比较均匀的情况
     */
    public static void basedSort(int[] randomArray) {
        //step 1 创建10个桶,step 2 放桶 倒桶 重复操作
        Queue<Integer>[] buckets = new LinkedList[10];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new LinkedList<>();
        }
        int digit = 1;
        int max = findMax(randomArray, 0, randomArray.length - 1);
        while (max != 0) {

            for (int num : randomArray) {
                int bucketIndex = (num / digit) % 10; // 假设数据范围是0-100
                buckets[bucketIndex].offer(num);
            }
            putElementsFromBucketsToArray(buckets, randomArray);
            max = max / 10;
            digit = digit * 10;
        }
    }

    public static void basedSort2(int[] randomArray) {
        //step 1 创建10个桶,step 2 放桶 倒桶 重复操作
        int digit = 1;

        int max = findMax(randomArray, 0, randomArray.length - 1);
        while (max != 0) {
            int[] tempArray = new int[10];
            int[] help = new int[randomArray.length];
            for (int i = randomArray.length - 1; i >= 0; i--) {
                int bucketIndex = (randomArray[i] / digit) % 10;
                tempArray[bucketIndex]++;
            }
            for (int i = 1; i < tempArray.length; i++) {
                tempArray[i] += tempArray[i - 1];
            }
            for (int i = randomArray.length - 1; i >= 0; i--) {
                // 将元素放到正确的位置
                int bucketIndex = (randomArray[i] / digit) % 10;
                help[tempArray[bucketIndex] - 1] = randomArray[i];
                tempArray[bucketIndex]--;
            }
            System.arraycopy(help, 0, randomArray, 0, help.length);
            max = max / 10;
            digit=digit*10;

        }
    }


    private static int findMax(int[] randomArray, int left, int right) {
        if (left == right) {
            return randomArray[left];
        }
        int maxLeft = findMax(randomArray, left, left + (right - left) / 2);
        int maxRight = findMax(randomArray, left + (right - left) / 2 + 1, right);
        return Math.max(maxLeft, maxRight);


    }

    private static void putElementsFromBucketsToArray(Queue<Integer>[] buckets, int[] randomArray) {
        int i = 0;
        for (Queue<Integer> bucket : buckets) {
            /**
             * 将每个桶中的元素取出放到原数组中
             */
            while (!bucket.isEmpty()) {
                randomArray[i++] = bucket.poll();

            }
        }
    }

    public static void main(String[] args) {
        int[] randomArray = {10, 5, 100, 2, 333, 909};
        basedSort2(randomArray);
        for (int i : randomArray) {
            System.out.println(i);
        }
    }

}
