package com.qiuxiang.algorithm.recursion;

/**
 * get the maximum value from an array using recursion.
 */
public class MaxDemo {


    public int getMax(int[]arr,int left,int right){
//        simple case
        if(left==right){
            return arr[left];
        }
        //divide
        int mid=(left+right)/2;
        int leftValue = getMax(arr,left,mid);
        int rightValue = getMax(arr,mid+1,right);
        //the actually max    value
        return Math.max(leftValue,rightValue);
    }


}
