package com.qiuxiang.algorithm.sort;

public class DNFPartitionDemo {

    public static void DNFPartition(int[] arr, int pivot) {
        if (arr == null || arr.length < 2) {
            return;
        }
        int low = 0; // low pointer for values less than pivot
        int mid = 0; // mid pointer for values equal to pivot
        // we use low pointer to track the position of the last value less than the pivot
        int high = arr.length - 1;
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
    }

    public static int[] AdditionDNFPartition(int[] arr, int L, int R) {
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
        for (int i : arr) {
            System.out.print(i + " ");
        }

        return new int[]{low, high};

    }

//    quicklySort(int[] arr)
public static void quicklySort(int[] arr, int L, int R) {
        if (L>=R) {
            return;
        }
        // 选择一个基准值，这里选择了数组的最后一个元素
    int M= partition(arr, L, R);
        quicklySort(arr, L, M-1);
        quicklySort(arr, M+1, R);

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


    private static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;

    }

    public static void main(String[] args) {
        int[] arr = {8, 2, 1, 7, 3, 4, 5, 5, 5, 6, 9, 10};


//            DNFPartition(arr, 5);

//        int[] ints = AdditionDNFPartition(arr, 1, 8);
        quicklySort(arr,0, arr.length - 1);
        for (int i : arr) {
            System.out.print(i + " ");
        }
        System.out.println();
        // Output should be: 3 2 1 4 5 6 7 8 9 10
    }





}
