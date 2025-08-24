package com.qiuxiang.algorithm.nodes.test;

import com.qiuxiang.algorithm.nodes.Node;

import java.awt.geom.Line2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

import static com.qiuxiang.algorithm.sort.QuicklySortDemo.swap;


/**
 * 链表的快慢指针.以下是练习题
 * 1.输入链表头节点,奇数长度返回中点,偶数长度返回上中点
 * 2.输入链表头节点,返回倒数第k个节点
 * 3.输入链表头节点,返回链表的中点
 * 4.输入链表头节点,返回链表的倒数第k个
 * 5.输入链表头节点,返回链表的中点
 * 6.输入链表头节点,返回链表的倒数第k个
 * 7.输入链表头节点,返回链表的中点
 * 8.输入链表头节点,返回链表的倒数第k个
 */
public class QuickSlowIndexNodes {


    public static RandNode getMidOrUpMidNode(RandNode head) {
        if (head == null || head.getNextNode() == null || head.getNextNode().getNextNode() == null) {
            return head;
        }
        RandNode slow = head.getNextNode();
        RandNode fast = head.getNextNode().getNextNode();
        while (fast != null && fast.getNextNode().getNextNode() != null) {
            slow = slow.getNextNode();
            fast = fast.getNextNode().getNextNode();
        }
        return slow;
    }

    public static RandNode getMidOrDownMidNode(RandNode head) {
        if (head == null) {
            return null;
        }
        RandNode slow = head;
        RandNode fast = head.getNextNode();
        while (fast != null && fast.getNextNode().getNextNode() != null) {
            slow = slow.getNextNode();
            fast = fast.getNextNode().getNextNode();
        }
        return slow.getNextNode();
    }

    /**
     * 回文node
     */

    public static boolean isPalindrome(Node head) {
        if (head == null || head.getNextNode() == null) {
            return true;
        }
        Node slow = head;
        Node fast = head.getNextNode();
        while (fast != null && fast.getNextNode() != null) {
            slow = slow.getNextNode();
            fast = fast.getNextNode().getNextNode();
        }
        Node mid = slow.getNextNode();
        Node reverseMid = Node.reverse(mid);
        while (reverseMid != null) {
            if (head.getValue() != reverseMid.getValue()) {
                return false;
            }
            head = head.getNextNode();
            reverseMid = reverseMid.getNextNode();
        }
        return true;
    }

    /**
     * 利用栈结构计算回文
     */

    public static boolean isPalindromeByStack(RandNode head) {
        if (head == null || head.getNextNode() == null) {
            return true;
        }
        RandNode slow = head;
        RandNode fast = head.getNextNode();
        Stack<Integer> intS = new Stack<>();
        while (fast != null && fast.getNextNode() != null) {
            intS.push(fast.getNextNode().getValue());
            slow = slow.getNextNode();
            fast = fast.getNextNode().getNextNode();
        }
        while (intS.size() > 0) {
            if (slow.getValue() != intS.pop()) {
                return false;
            }
            slow = slow.getNextNode();

        }
        return true;

    }

    /**
     * 将单链表按照某值划分为左边小,中间相等,右边大的形式
     */

    public RandNode listPartition(RandNode head, int pivot) {
        RandNode sH = null; // small head
        RandNode sT = null; // small tail
        RandNode eH = null; // equal head
        RandNode eT = null; // equal tail
        RandNode bH = null; // big head
        RandNode bT = null; // big tail
        RandNode next = null; // save next node
        // every node distributed to three lists
        while (head != null) {
            next = head.getNextNode();
            head.setNextNode(null);
            if (head.getValue() < pivot) {
                if (sH == null) {
                    sH = head;
                    sT = head;
                } else {
                    sT.setNextNode(head);
                    sT = head;
                }
            } else if (head.getValue() == pivot) {
                if (eH == null) {
                    eH = head;
                    eT = head;
                } else {
                    eT.setNextNode(head);
                    eT = head;
                }
            } else {
                if (bH == null) {
                    bH = head;
                    bT = head;
                } else {
                    bT.setNextNode(head);
                    bT = head;
                }
            }
            head = next;
        }
        // small and equal reconnect
        if (sT != null) { // small list is not empty
            sT.setNextNode(eH);
            eT = eT == null ? sT : eT; // if equal list is empty, eT should be the end of small list
        }
        // all reconnect
        if (eT != null) { // equal list or small list is not empty
            eT.setNextNode(bH);
        }
        return sH != null ? sH : (eH != null ? eH : bH);
    }

    /**
     * 将单链表按照某值划分为左边小,中间相等,右边大的形式 利用数组转
     *
     * @param head
     * @param pivot
     * @return
     */
    public RandNode listPartition2(RandNode head, int pivot) {
//        先遍历链表拿到长度
        if (head == null) {
            return null;
        }
        int len = 0;
        RandNode cur = head;
        while (cur != null) {
            len++;
            cur = cur.getNextNode();
        }


        int[] arr = new int[len];
        int i = 0;
        while (head.getNextNode() != null) {
            head = head.getNextNode();
            arr[i++] = head.getValue();
        }
        partition(arr, pivot);
        RandNode newHead = new RandNode(arr[0], null);
        RandNode newCur = newHead;
        for (int j = 1; j < arr.length; j++) {
            newCur.setNextNode(new RandNode(arr[j], null));
            newCur = newCur.getNextNode();
        }
        return newHead;
    }


    public static void partition(int[] arr, int pivot) {

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

    /**
     * 利用hashMap去复制一个特殊的node. 包含一个rand指针
     */
    static class RandNode {
        int value;
        RandNode nextRandNode;
        RandNode randNode;

        public RandNode(int value, RandNode nextRandNode) {
            this.value = value;
            this.nextRandNode = nextRandNode;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public RandNode getNextNode() {
            return nextRandNode;
        }

        public void setNextNode(RandNode nextRandNode) {
            this.nextRandNode = nextRandNode;
        }

        public RandNode getRandNode() {
            return randNode;
        }

        public void setRandNode(RandNode randNode) {
            this.randNode = randNode;
        }
    }

    public static RandNode copySpecialNode(RandNode head) {
        if (head == null) {
            return null;
        }
        java.util.HashMap<RandNode, RandNode> map = new java.util.HashMap<>();
        RandNode cur = head;
        while (cur != null) {
            map.put(cur, new RandNode(cur.getValue(), null));
            cur = cur.getNextNode();
        }
        cur = head;
        while (cur != null) {
            map.get(cur).setNextNode(map.get(cur.getNextNode()));
            map.get(cur).setRandNode(map.get(cur.getRandNode()));
            cur = cur.getNextNode();
        }
        return map.get(head);
    }

    /**
     * 1->1'->2->2'->3->3'->null
     *
     * @param head
     * @return
     */
    public static RandNode copySpecialNode2(RandNode head) {
        RandNode newHead = new RandNode(0, null);
        if (head == null) {
            return null;
        }
        RandNode copyNode = null;
        RandNode cur = head;
        while (cur != null) {
            // 复制节点
            copyNode = new RandNode(cur.getValue(), cur.getNextNode());
            // 将复制的节点插入到原节点后面
            cur.setNextNode(copyNode);
            // 移动到下一个原节点
            cur = copyNode.getNextNode();
        }
        cur = head;
        while (cur != null) {
            cur.nextRandNode.randNode = cur.getRandNode().getNextNode();
            cur = cur.nextRandNode.nextRandNode;
        }
        // 从老节点中进行剥离
//        1.准备变量 保存当前的node信息
//        2,连接到newNode上
        RandNode newCur = null;
        newCur = newHead;
        while (head != null) {
            newCur.nextRandNode = head.nextRandNode;
            // 移动到下一个原节点
            newCur = newCur.getNextNode();
            head = head.nextRandNode.nextRandNode;

        }
        return newHead.nextRandNode;
    }

    public static void main(String[] args) {
        //test copySpecialNode2
        RandNode node1 = new RandNode(1, null);
        RandNode node2 = new RandNode(2, null);
        RandNode node3 = new RandNode(3, null);
        node1.setNextNode(node2);
        node2.setNextNode(node3);
        node1.setRandNode(node2);
        node2.setRandNode(node3);
        node3.setRandNode(node1);
        node3.setNextNode(null);
        RandNode randNode = copySpecialNode2(node1);
        while (randNode != null) {
            System.out.println("Value: " + randNode.getValue() +
                    ", Next: " + (randNode.getNextNode() != null ? randNode.getNextNode().getValue() : "null") +
                    ", Rand: " + (randNode.getRandNode() != null ? randNode.getRandNode().getValue() : "null"));
            randNode = randNode.getNextNode();

        }
    }

    /**
     * 给定两个可能有环也可能无环的单链表,实现一个函数,如果两个链表相交,请返回相交的第一个节点,如果不相交返回null,头节点分别为head1 和head2
     * 1.先判断两个链表是否有环,如果有环,返回入环节点,如果无环,返回null
     * 2.如果两个链表都无环,则利用长度差值法,找到第一个相交节点
     * 3.如果两个链表都有环,则分为两种情况,入环节点相同,则利用长度差值法,找到第一个相交节点
     */


    public Node getIntersectNode(Node head1, Node head2) {
        if (head1 == null || head2 == null) {
            return null;
        }
        Node loop1 = getLoopNode(head1);
        Node loop2 = getLoopNode(head2);
        if (loop1 == null && loop2 == null) {
            return noLoop(head1, head2);
        }
        if (loop1 != null && loop2 != null) {
            return bothLoop(head1, loop1, head2, loop2);
        }
        return null;
    }

    private Node bothLoop(Node head1, Node loop1, Node head2, Node loop2) {
        if (loop1 == null && loop2 == null) {
            return null; // 两个链表都无环
        }
        if (loop1 == null && loop2 != null) {
            return null; // 一个链表无环,一个链表有环
        }
        if (loop1 != null && loop2 == null) {
            return null; // 一个链表有环,一个链表无环
        }
        if (loop1 == loop2) {
//            此时需判断 两个链表在入环节点之前是否相交
            Node cur1 = head1;
            Node cur2 = head2;
            int len1 = 0;
            int len2 = 0;
            while (cur1 != loop1) {
                len1++;
                cur1 = cur1.getNextNode();
            }
            while (cur2 != loop2) {
                len2++;
                cur2 = cur2.getNextNode();
            }
            cur1 = head1;
            cur2 = head2;
            if (len1 > len2) {
                while (len1 > len2) {
                    cur1 = cur1.getNextNode();
                    len1--;
                }
            } else {
                while (len2 > len1) {
                    cur2 = cur2.getNextNode();
                    len2--;
                }
            }
            while (len1 > 0 && cur1 != null && cur2 != null) {
                if (cur1 == cur2) {
                    return cur1; // 返回相交节点
                }
                cur1 = cur1.getNextNode();
                cur2 = cur2.getNextNode();
            }

            return loop1;
        } else {
            Node cur1 = loop1.getNextNode();
            while (cur1 != loop1) {
                if (cur1 == loop2) {
                    return cur1; // 返回相交节点
                }
                cur1 = cur1.getNextNode();
            }
            return null; // 两个链表有环,但入环节点不同
        }
    }

    /**
     * 两个链表都有环,入环节点相同,
     * 没有环时,比对两个链表的长度,如果长度相同,则直接比较节点是否相同,如果长度不同,则先走长度差值的节点,然后再比较节点是否相同
     *
     * @param head1
     * @param head2
     * @return
     */
    private Node noLoop(Node head1, Node head2) {
        int lenth1 = 0;
        int lenth2 = 0;
        Node entry1 = head1;
        Node entry2 = head2;
        while (entry1 != null) {
            lenth1++;
            entry1 = entry1.getNextNode();
        }
        while (entry2 != null) {
            lenth2++;
            entry2 = entry2.getNextNode();
        }
        entry2 = head2;
        entry1 = head1;
        if (lenth1 > lenth2) {
            while (lenth1 > lenth2) {
                entry2 = entry2.getNextNode();
                lenth1--;
            }
        } else {
            while (lenth2 > lenth1) {
                entry1 = entry1.getNextNode();
                lenth2--;
            }
        }
        while (lenth1 > 0 && entry1 != null && entry2 != null) {
            if (entry1 == entry2) {
                return entry1; // 返回相交节点
            }
            entry1 = entry1.getNextNode();
            entry2 = entry2.getNextNode();
        }

        return null;
    }

    private Node getLoopNode(Node head) {

        Node loop = head;
        Node fast = head.getNextNode();
        Node slow = head;

        while (loop != null) {
            slow = slow.getNextNode();
            fast = fast.getNextNode().getNextNode();
            if (slow == fast) {
                // 找到环的入口
                Node entry = head;
                while (entry != slow) {
                    entry = entry.getNextNode();
                    slow = slow.getNextNode();
                }
                return entry; // 返回入环节点
            }
        }
        if (fast == null || fast.getNextNode() == null) {
            return null; // 无环
        }
        return fast; // 返回入环节点
    }

    public Node getIntersectNode2(Node head1, Node head2) {
        LinkedHashMap<Node, Integer> nodeIntegerLinkedHashMap = new LinkedHashMap<>();
        LinkedHashMap<Node, Integer> nodeInteger2LinkedHashMap = new LinkedHashMap<>();
        Node node1 = null;
        Node node2 = null;
        while (head1 != null) {
            head1 = head1.getNextNode();
            if (nodeIntegerLinkedHashMap.containsKey(head1)) {
                node1 = head1;
                break;
            }
        }
        while (head2 != null) {
            head2 = head2.getNextNode();
            if (nodeInteger2LinkedHashMap.containsKey(head2)) {
                node2 = head2;
                break;
            }
        }
        for (Node node : nodeIntegerLinkedHashMap.keySet()) {
            for (Node node3 : nodeInteger2LinkedHashMap.keySet()) {
                if (node == node3) {
                    return node;
                }
            }

        }
        return null;
    }
    /**
     * 给任意节点,在链表中讲该节点删除,并返回删除后的链表头节点
     * 下面方法的问题是,如果删除的是尾节点,则会导致链表断裂,所以需要特殊处理
     * 另外,如果删除的是头节点,则需要返回新的头节点
     * @param node
     * @return
     * @deprecated
     */

    public static void removeNode(Node node) {
        if (node.getNextNode()==null){
            node=null;
            return;
        }
        Node temp= node.getNextNode();
        Node nextNode = temp.getNextNode().getNextNode();
        node=temp;
        node.setNextNode(nextNode);

    }
}
