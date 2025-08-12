package com.qiuxiang.algorithm.sort;

public class SelectSortDemo {

    /**
     * 选择排序最大的问题在于浪费了很多次交换,注定性能不会太好
     * @param randomArray
     */
    public static void selectSort(int[] randomArray){
        if (randomArray == null || randomArray.length < 2){
            return;
        }
        for (int i = 0; i < randomArray.length; i++) {
            int minIndex = i;
            // 假设当前元素为最小值
            for (int j = i + 1; j < randomArray.length; j++) {
                if (randomArray[j] < randomArray[minIndex]){
                    minIndex=j;
                }
            }


            // 如果找到的最小值不是当前元素，则交换
            swap(randomArray, i, minIndex);
        }
    }

    private static void swap(int[] randomArray, int i,int minIndex ) {
        if (minIndex != i) {
            randomArray[i] = randomArray[i]^randomArray[minIndex];
            randomArray[minIndex] = randomArray[i]^randomArray[minIndex];
            randomArray[i] = randomArray[i]^randomArray[minIndex];
        }
    }

    public static void main(String[] args) {
        for (int i = args.length - 1; i >= 0; i--) {
            System.out.println(args[i]);
        }
    }


}
