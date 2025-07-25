package com.qiuxiang.algorithm.nodes;

public class Node {
    private int value;

    private Node nextNode;

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public Node getNextNode() {
        return nextNode;
    }

    public void setNextNode(Node nextNode) {
        this.nextNode = nextNode;
    }

    public Node(int value, Node nextNode) {
        this.value = value;
        this.nextNode = nextNode;
    }

    /**
     * Reverse the linked list starting from this node.
     *
     * @return
     */
    public static Node reverse(Node node) {
        Node preNode = null;
        Node nextNode;
        if (node.nextNode == null) {
            return node;
        }

        while (node != null) {
            nextNode = node.nextNode;
//            核心,使用prenode 保存上一个节点的引用
            node.nextNode = preNode;
            // 更新preNode为当前节点,指针向前移动
            preNode = node;
            node = nextNode;
        }


        return preNode;
    }


}
