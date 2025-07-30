package com.qiuxiang.algorithm.heap;

/**
 * i from 0
 * 父节点 = (i - 1) / 2
 * 左子节点 = 2 * i + 1
 * 右子节点 = 2 * i + 2
 * <p> * 堆的插入和删除操作
 * 插入操作：将新元素放在堆的末尾，然后向上调整（上浮）以保持堆的性质。
 */
public class HeapDemo {
    private int[] heap= new int[Integer.MAX_VALUE];
    private static int size;

    public static void insert(int[] heap, int index) {
        while (index < heap.length && heap[index] > heap[(index - 1) / 2]) {
            // 如果当前节点大于父节点，则交换
            swap(heap, index, (index - 1) / 2);
            index = (index - 1) / 2; // 更新索引到父节点
            size++;
        }
    }


    private static void swap(int[] randomArray, int i, int minIndex) {
        if (minIndex != i && randomArray[minIndex] != randomArray[i]) {
            randomArray[i] = randomArray[i] ^ randomArray[minIndex];
            randomArray[minIndex] = randomArray[i] ^ randomArray[minIndex];
            randomArray[i] = randomArray[i] ^ randomArray[minIndex];
        }
    }

    public static int pop(int[] heap) {
        int max = heap[0];
        swap(heap, size, 0);
        heapify(heap, 0, size--);
        return max;


    }
    public static int popMin(int[] heap,int size ) {
        int min = heap[0];
        swap(heap, size, 0);
        minHeapify2(heap, 0, size);
        return min;
    }


    private static void heapify(int[] heap, int index, int length) {

        int left = 2 * index + 1;
        int right = 2 * index + 2;
        while (left < length) {
            int largest = left;
            if (right < length && heap[right] > heap[left]) {
                largest = right; // 确保 left 是较大的子节点
            }
            if (heap[index] >= heap[largest]) {
                break; // 如果当前节点大于等于较大的子节点，则不需要调整
            }
            swap(heap, index, largest);
            //调整左右节点,确保大根堆
            // 如果当前节点小于较大的子节点，则交换
            index = largest; // 更新索引到下一个需要调整的节点
            left = 2 * index + 1; // 更新左子节点索引
            right = 2 * index + 2; // 更新右子节点索引
        }
    }

    private static void heapify2(int[] heap, int index) {

      while (index>0 && heap[index] >=  heap[(index - 1)/2]) {
          swap(heap, index, (index - 1)/2);
            index = (index - 1)/2; // 更新索引到父节点
      }
    }

    public static void heapSort(int[] arr) {
        // 构建最大堆
       int size = arr.length;
        for (int i = 0; i < arr.length; i++) {
            insert(arr, i);
        }

        while (size > 0) {
            swap(arr, 0, --size);
            heapify(arr, 0, size);
        }
    }
    public static void heapSort2(int[] arr) {
        // 构建最大堆
        int size = arr.length;
        for (int i = size - 1; i >= 0; i--) {
            heapify2(arr, i);
        }

        // 进行排序
        for (int i = size - 1; i > 0; i--) {
            swap(arr, 0, i);
            heapify(arr, 0, i);
        }
    }


    public static void main(String[] args) {
        int[] arr = {5,2,1,10,3};
        heapSort2(arr);
        for (int i : arr) {
            System.out.print(i + " ");
        }
        System.out.println();

        // 测试插入
        int[] heap = new int[]{3,10,1,2,5};
        minInsert(heap, 0);
        minInsert(heap, 1);
        minInsert(heap, 2);
        minInsert(heap, 3);
        minInsert(heap, 4);
        minHeapSort(heap);
        // 测试弹出最大元素
        System.out.println("Max element: " + pop(heap));
    }

    private static void minInsert(int[]arr, int index) {
        while (index < arr.length && arr[index] < arr[(index - 1) / 2]) {
            // 如果当前节点小于父节点，则交换
            swap(arr, index, (index - 1) / 2);
            index = (index - 1) / 2; // 更新索引到父节点
        }
        size++;
    }

    public static void minHeapify(int[]arr, int index,int size ) {
        while (index > 0 && arr[index] < arr[(index - 1) / 2]) {
            swap(arr, index, (index - 1) / 2);
            index = (index - 1) / 2; // 更新索引到父节点
        }
    }
    public static void minHeapify2(int[]arr, int index,int size ) {
        int left = 2 * index + 1;
        int right = 2 * index + 2;
        while (left < size) {
            int smallest = left;
            if (right < size && arr[right] < arr[left]) {
                smallest = right; // 确保 left 是较小的子节点
            }
            if (arr[index] <= arr[smallest]) {
                break; // 如果当前节点小于等于较小的子节点，则不需要调整
            }
            swap(arr, index, smallest);
            //调整左右节点,确保小根堆
            // 如果当前节点大于较小的子节点，则交换
            index = smallest; // 更新索引到下一个需要调整的节点
            left = 2 * index + 1; // 更新左子节点索引
            right = 2 * index + 2; // 更新右子节点索引
        }
    }


    //min heapify sort
    public static void minHeapSort(int[] arr) {
        // 构建最小堆
        int size = arr.length;
        for (int i = arr.length - 1; i > 0; i--) {
            minHeapify(arr, i, size);
        }

        sortMinHeap(arr,arr.length);
    }

    private static void sortMinHeap(int[] arr,  int size) {
        int[] temp = new int[size];
        int orginalSize = size;
        for (int i = 0; i < size; i++) {
            temp[i] = popMin(arr,--orginalSize);

        }
        System.arraycopy(temp, 0, arr, 0, size);
    }

    //for an arr almost sorted,so for each element larger move is k, using heap sort is O(nlogk)to sort it
    // k is the maximum distance an element can be from its sorted position
    // space complexity is O(k), and time complexity is O(nlogk)
    public void sortKArray(int [] arr, int k) {
        if (arr == null || arr.length < 2) {
            return;
        }
        int size = arr.length;
        for (int i = 0; i < size; i++) {
            minInsert(arr, i);
            if (i >= k) {
                popMin(arr, size);
            }
        }
        // 将剩余的元素弹出
        while (size > 0) {
            popMin(arr, size--);
        }

    }


}
