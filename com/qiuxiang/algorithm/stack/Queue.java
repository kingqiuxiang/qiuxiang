package com.qiuxiang.algorithm.stack;

public class Queue {
    private int[] queue;
    private int popIndex = 0;
    private int size = 0;
    private int index = 0;
    private int limit = DEFAULT_CAPACITY;
    private static final int DEFAULT_CAPACITY = 10;

    public Queue() {
        queue = new int[DEFAULT_CAPACITY];
    }

    public Queue(int limit) {
        this.limit = limit;
        queue = new int[limit];
    }

    public void push(int value) {
        if (size >= limit) {
            throw new RuntimeException("Queue is full");
        }
        queue[index] = value;
        index = calculateIndex(index);
        size++;
    }

    private int calculateIndex(int index) {
        if (index >= limit - 1) {
            index = 0;
        } else {
            index++;
        }
        return index;
    }

    public int pop() {
        if (size == 0) {
            throw new RuntimeException("queue is empty");
        }
        int value = queue[popIndex];
        size--;
        popIndex = calculateIndex(popIndex);
        return value;
    }


}
