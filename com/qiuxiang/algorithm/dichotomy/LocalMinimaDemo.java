package com.qiuxiang.algorithm.dichotomy;

public class LocalMinimaDemo {

    public static int localMinima(int[] arr) {
//       判断一个数组的局部最小值
        int left = 0;
        int right = arr.length - 1;
        int mid = 0;
        while (left < right) {
            mid = left + (right - left) / 2;
           if (arr[mid] < arr[mid + 1]) {
                // 如果中间元素小于右边的元素，则局部最小值在左边或中间
                right = mid;
              } else {
                // 如果中间元素大于等于右边的元素，则局部最小值在右边或中间
                left = mid + 1;
           }
        }
        return left;
    }

    /**
     * 找到最高位的1
     * @param i
     * @return
     */
  static int findLowPosition(int i){
        return i&(~i+1);
   }
   public static void printOddTimesNum(int[] arr){
      /*
      * for array have two numbers that appear odd times, find them
      * */
         int xor = 0;
         for (int num : arr) {
              xor ^= num;
         }
         // 找到最低位的1
         int lowPosition = findLowPosition(xor);
         int num1 = 0, num2 = 0;
         for (int num : arr) {
              if ((num & lowPosition) == 0) {
                num1 ^= num; // 在低位为0的分组中异或
              }
         }
         num2=xor^num1; // 通过总异或值减去第一个数得到第二个数
         System.out.println("The two numbers that appear odd times are: " + num1 + " and " + num2);


   }


    public static void main(String[] args) {
        int lowPosition = findLowPosition(7);
        System.out.println("Lowest position of 1 in binary representation: " + lowPosition);
        int[] arr = {2,2,2,5,5,5,6,6,7,7,8,8,9,9,10,10};
        printOddTimesNum(arr);

    }


}
