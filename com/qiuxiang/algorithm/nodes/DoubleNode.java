package com.qiuxiang.algorithm.nodes;

public class DoubleNode {
    private int value;
    private DoubleNode pre;
    private DoubleNode next;

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public DoubleNode getPre() {
        return pre;
    }

    public void setPre(DoubleNode pre) {
        this.pre = pre;
    }

    public DoubleNode getNext() {
        return next;
    }

    public void setNext(DoubleNode next) {
        this.next = next;
    }

    public DoubleNode(int value, DoubleNode pre, DoubleNode next) {
        this.value = value;
        this.pre = pre;
        this.next = next;
    }

    public DoubleNode() {
    }

    /**
     * 核心思想就是保证后续的节点的pre指向当前节点，并且不在持有之前的节点的引用。
     *
     * @param head
     * @return
     */
    public static DoubleNode reserve(DoubleNode head) {
        if (head == null) {
            return null;
        }
        DoubleNode pre = null;
        DoubleNode next;
        while (head != null) {
            next = head.next;

            head.next = pre;
            head.pre = next;

            pre = head;
            // 这里的next是原来的head.next,所以需要将head的pre指向next
            head = next;
        }
        return pre;
    }


}
