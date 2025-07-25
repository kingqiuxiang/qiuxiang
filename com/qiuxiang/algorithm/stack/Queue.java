package com.qiuxiang.algorithm.stack;

public class Stack {
    private int[] stack;
    private int popIndex = 0;
    private int size = 0;
    private int index = 0;
    private int limit = DEFAULT_CAPACITY;
    private static final int DEFAULT_CAPACITY = 10;

    public Stack() {
        stack = new int[DEFAULT_CAPACITY];
    }

    public Stack(int limit) {
        this.limit = limit;
        stack = new int[limit];
    }

    public int push(int value) {
        if (size >= limit) {
            throw new StackOverflowError();
        }
        index = calculateIndex(index);
        size++;
        return stack[index] = value;
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
            throw new IllegalStateException("Stack is empty");
        }
        int value = stack[popIndex];
        size--;
        popIndex = calculateIndex(popIndex);
        return value;
    }


}
