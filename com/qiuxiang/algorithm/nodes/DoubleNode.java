package com.qiuxiang.algorithm.nodes;

public class DubbleNode {
    private int value;
    private DubbleNode pre;
    private DubbleNode next;

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public DubbleNode getPre() {
        return pre;
    }

    public void setPre(DubbleNode pre) {
        this.pre = pre;
    }

    public DubbleNode getNext() {
        return next;
    }

    public void setNext(DubbleNode next) {
        this.next = next;
    }

    public DubbleNode(int value, DubbleNode pre, DubbleNode next) {
        this.value = value;
        this.pre = pre;
        this.next = next;
    }

    public DubbleNode() {
    }

    /**
     * 核心思想就是保证后续的节点的pre指向当前节点，并且不在持有之前的节点的引用。
     * @param head
     * @return
     */
    public static DubbleNode reserve(DubbleNode head) {
        if (head == null) {
            return null;
        }
        DubbleNode pre = null;
        DubbleNode next;
        while (head != null) {
            next=head.next;
            head.next=pre;
            head.pre=next;

            pre=head;
            // 这里的next是原来的head.next,所以需要将head的pre指向next
            head=next;
        }
        return pre;
    }


}
