package com.qiuxiang.algorithm;

import com.qiuxiang.algorithm.nodes.DoubleNode;
import com.qiuxiang.algorithm.nodes.Node;

public class NodeMainDemo {
    public static void main(String[] args) {
//        testNode();
        testDoubleNode();

    }

    private static void testDoubleNode() {
        DoubleNode node5 = new DoubleNode(5, null, null);
        DoubleNode node4 = new DoubleNode(4, null, node5);
        DoubleNode node3 = new DoubleNode(3, null, node4);
        DoubleNode node2 = new DoubleNode(2, null, node3);
        DoubleNode node1 = new DoubleNode(1, null, node2);
        node5.setPre(node4);
        node4.setPre(node3);
        node3.setPre(node2);
        node2.setPre(node1);
        DoubleNode reserve = DoubleNode.reserve(node1);
        System.out.println(reserve.getValue() + " ");
        removeNodes(reserve, 5);
    }

    private static void testNode() {
        Node node5 = new Node(5, null);
        Node node4 = new Node(4, node5);
        Node node3 = new Node(3, node4);
        Node node2 = new Node(2, node3);
        Node node1 = new Node(1, node2);


        // 打印链表
        Node currentNode = node1;
        while (currentNode != null) {
            System.out.print(currentNode.getValue() + " ");
            currentNode = currentNode.getNextNode();
        }
        Node reverse = Node.reverse(node1);

        currentNode = reverse;
        while (currentNode != null) {
            System.out.print(currentNode.getValue() + " ");
            currentNode = currentNode.getNextNode();
        }
    }

    public static DoubleNode removeNodes(DoubleNode node, int num) {
        while (node != null) {
            if (node.getValue() != num) {
                break;

            }
            node = node.getNext();
            node.setPre(null);
        }
        DoubleNode current = node;
        DoubleNode previous = node;
        while (current != null) {
            if (current.getValue() == num) {
                previous.setNext(current);
                current.setNext(previous);
            }
            current = current.getNext();
        }
        return node;
    }

}
