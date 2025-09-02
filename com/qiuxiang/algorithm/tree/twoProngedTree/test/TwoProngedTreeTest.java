package com.qiuxiang.algorithm.tree.twoProngedTree.test;

import com.qiuxiang.algorithm.tree.twoProngedTree.TwoProngedTree;

import java.security.PublicKey;
import java.util.List;

public class TwoProngedTreeTest {


    public static void main(String[] args) {
        //构造一个二叉树
        Node<Integer> node1 = new Node<>();
        node1.value = 1;
        Node<Integer> node2 = new Node<>();
        node2.value = 2;
        Node<Integer> node3 = new Node<>();
        node3.value = 3;
        Node<Integer> node4 = new Node<>();
        node4.value = 4;
        Node<Integer> node5 = new Node<>();
        node5.value = 5;
        Node<Integer> node6 = new Node<>();
        node6.value = 6;
        Node<Integer> node7 = new Node<>();
        node7.value = 7;
        node1.left = node2;
        node1.right = node3;
        node2.parent = node1;
        node3.parent = node1;
        node2.left = node4;
        node2.right = node5;
        node4.parent = node2;
        node5.parent = node2;
        node3.left = node6;
        node3.right = node7;
        node6.parent = node3;
        node7.parent = node3;


//        findNextNode(node1);
        printSpecialNode(3);

    }

    /**
     * 根据当前节点找出后继节点,假设中序遍历的情况
     *
     * @param node
     */
    private static Node findNextNode(Node node) {
        if (node.right != null) {
            return getLeftMost(node.right);
        }
        return getNodeNextParent(node);
    }

    private static Node getLeftMost(Node right) {
        if (right.left != null) {
            return getLeftMost(right.left);
        }
        return right;
    }

    private static Node getNodeNextParent(Node node) {
        if (node.parent != null) {
            Node parent = node.parent;
            if (parent.left == node) {
                return parent;
            } else {
                return getNodeNextParent(parent);
            }
        }
        return null;
    }


    public static class Node<V> {
        V value;
        Node<V> left;
        Node<V> right;
        Node<V> parent;

    }

    /**
     * 请把一段纸条竖着放在桌子上,然后从纸条的下边向上方对折一次,压出折痕,折痕是凹下去的还是凸上去的?
     * 如果从下边向上方对折两次,折痕是怎样的?
     * 如果从下边向上方对折三次,折痕是怎样的?
     * 给定一个输入参数N,代表纸条都从下边向上方对折N次,请从上到下打印所有的折痕方向
     * 例如:N=1,打印: down
     * N=2,打印: down down up
     * N=3,打印: down down up down down up up
     *
     * @param N
     */
    public static void printSpecialNode(int N) {
        printDownUp(1, N, true);


    }

    private static void printDownUp(int i, int n, boolean b) {
        if (i > n) {
            return;
        }
        printDownUp(i + 1, n, true);
        System.out.println(b ? "down" : "up");
        printDownUp(i + 1, n, false);

    }

    /**
     * 给定二叉树头节点, head,判断该树是不是平衡二叉树
     */
    public boolean isBalance(TwoProngedTree head) {
        return process(head).isBalance;
    }

    public static class Info {
        public boolean isBalance;
        public int height;

        public Info(boolean isBalance, int height) {
            this.isBalance = isBalance;
            this.height = height;
        }
    }

    public static Info process(TwoProngedTree x) {
        if (x == null) {
            return new Info(true, 0);
        }
        Info leftInfo = process(x.left);
        Info rightInfo = process(x.right);
        int height = Math.max(leftInfo.height, rightInfo.height) + 1;
        boolean isBalance = true;
        if (!leftInfo.isBalance) {
            isBalance = false;
        }
        if (!rightInfo.isBalance) {
            isBalance = false;
        }
        if (Math.abs(leftInfo.height - rightInfo.height) > 1) {
            isBalance = false;
        }
        return new Info(isBalance, height);
    }

    /**
     * 我来个手写版本的,方式有点像后序遍历,但其实并不是,它是一个递归序
     *
     * @param x
     * @return
     */

    public static Info process2(TwoProngedTree x) {
        if (x == null) {
            return new Info(true, 0);
        }
        boolean isBalance = true;
        Info left = process2(x.left);
        Info right = process2(x.right);
        int height = Math.max(left.height, right.height) + 1;
        if (left.isBalance && right.isBalance && left.height == right.height) {
            return new Info(isBalance, height);
        } else {
            return new Info(false, height);

        }
    }

    /**
     * 给定一个二叉树的头节点,任何两个节点之间都有距离,返回正科二叉树的最大距离
     */

    public int calculateMaxDistance(TwoProngedTree head) {
        if (head == null) {
            return 0;
        }
        return doMaxDistance(head).maxDistance;
    }

    private InfoDis doMaxDistance(TwoProngedTree head) {
        if (head == null) {
            return new InfoDis(0, 0);
        }
        InfoDis left = doMaxDistance(head.left);
        InfoDis right = doMaxDistance(head.right);

        int height = Math.max(left.height, right.height) + 1;
        int maxDistance = Math.max(Math.max(left.maxDistance, right.maxDistance), left.height + right.height + 1);
        return new InfoDis(maxDistance, height);


    }

    public class InfoDis {
        public int maxDistance;
        public int height;

        public InfoDis(int maxDistance, int height) {
            this.maxDistance = maxDistance;
            this.height = height;
        }
    }


    /**
     * 给定一个二叉树的头节点,返回二叉树中最大的二叉搜索子树的头节点
     */
    public static int maxSearchTree(TwoProngedTree head) {
        if (head == null) {
            return 0;
        }
        return process3(head).subSize;
    }

    public static MaxSearchInfo process3(TwoProngedTree head) {
        if (head == null) {
            return null;
        }
        MaxSearchInfo left = process3(head.left);
        MaxSearchInfo right = process3(head.right);
        int min = head.root;
        int max = head.root;
        int subSize = 0;
        boolean balance = false;
        if (left != null) {
            min = Math.min(min, left.minValue);
            max = Math.max(max, left.maxValue);
            subSize = Math.max(subSize, left.subSize);
        }
        if (right != null) {
            min = Math.min(min, right.minValue);
            max = Math.max(max, right.maxValue);
            subSize = Math.max(subSize, right.subSize);
        }


        if ((left == null || (left.isSearchTree && left.maxValue < head.root))
                && (right == null || (right.isSearchTree && right.minValue > head.root))) {
            balance = true;
            subSize = (left == null ? 0 : left.subSize) + (right == null ? 0 : right.subSize) + 1;
        }
        return new MaxSearchInfo(balance, subSize, min, max);
    }

    public static class MaxSearchInfo {
        public boolean isSearchTree;
        public int subSize;
        public int minValue;
        public int maxValue;

        public MaxSearchInfo(boolean isSearchTree, int subSize) {
            this.isSearchTree = isSearchTree;
            this.subSize = subSize;
        }

        public MaxSearchInfo(boolean isSearchTree, int subSize, int minValue, int maxValue) {
            this.isSearchTree = isSearchTree;
            this.subSize = subSize;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }
    }

    public static class NodeTT<V> {
        V value;
        public List<NodeTT<V>> children;

        public NodeTT(V value, List<NodeTT<V>> children) {
            this.value = value;
            this.children = children;
        }

        public NodeTT() {
        }
    }

    /**
     * 员工最大快乐值问题
     */
    public int getMaxHappy(NodeTT<Integer> head) {
        if (head == null) {
            return 0;
        }
        InfoHappy allInfo = processHappy(head);
        return Math.max(allInfo.yes, allInfo.no);


    }

    private InfoHappy processHappy(NodeTT<Integer> head) {
        if (head.children.isEmpty()) {
            return new InfoHappy(1, 0);
        }
        int yes = head.value;
        int no = 0;
        for (NodeTT<Integer> child : head.children) {
            InfoHappy childInfo = processHappy(child);
            //如果我来,我的孩子们就不能来
            yes += childInfo.no;
            //如果我不来,我的孩子们可以来,也可以不来
            no += Math.max(childInfo.yes, childInfo.no);
        }
        return new InfoHappy(yes, no);
    }

    public static class InfoHappy {
        private int yes;
        private int no;

        public InfoHappy(int yes, int no) {
            this.yes = yes;
            this.no = no;
        }

        public InfoHappy() {
        }
    }

    public static class InfoFullExactly {
        private boolean full;
        private boolean exact;
        private int height;

        public InfoFullExactly(boolean full, boolean exact, int height) {
            this.full = full;
            this.exact = exact;
            this.height = height;
        }
    }

    /**
     * 判断二叉树是不是平衡二叉树
     */

    public boolean isFullExactly(TwoProngedTree head) {
        if (head == null) {
            return true;
        }
        return processFull(head).exact;


    }

    private InfoFullExactly processFull(TwoProngedTree head) {
        if (head == null) {
            return new InfoFullExactly(true, true, 0);
        }
        InfoFullExactly left = processFull(head.left);
        InfoFullExactly right = processFull(head.right);
        int height = Math.max(left.height, right.height) + 1;
        boolean full = left.full && right.full && (left.height == right.height);
        boolean exact = false;
        if (left.exact && right.exact) {
            if (!right.full && left.height == right.height) {
                exact = true;
            }
            if (!left.full && right.full && left.height - right.height == 1) {
                exact = true;
            }
        }
        return new InfoFullExactly(full, exact, height);
    }

    /**
     * 给定一个二叉树的头节点head,和另外两个节点a和b,返回a和b的最低公共祖先节点
     */
    public static class InfoLowestAncestor {
        public boolean findA;
        public boolean findB;
        public TwoProngedTree lowestAncestor;

        public InfoLowestAncestor(boolean findA, boolean findB, TwoProngedTree lowestAncestor) {
            this.findA = findA;
            this.findB = findB;
            this.lowestAncestor = lowestAncestor;
        }
    }

    public TwoProngedTree lowestAncestor(TwoProngedTree head, TwoProngedTree a, TwoProngedTree b) {
        if (head == null || a == null || b == null) {
            return null;
        }
        return processLowestAncestor(head, a, b).lowestAncestor;
    }

    private InfoLowestAncestor processLowestAncestor(TwoProngedTree head, TwoProngedTree a, TwoProngedTree b) {
        if (head == null) {
            return new InfoLowestAncestor(false, false, head);
        }
        InfoLowestAncestor left = processLowestAncestor(head.left, a, b);
        InfoLowestAncestor right = processLowestAncestor(head.right, a, b);
        boolean findA = left.findA || right.findA || a == head;
        boolean findB = left.findB || right.findB || b == head;
        if (left.findA && left.findB) {
            return left;
        }
        if (right.findA && right.findB) {
            return right;
        }
        return new InfoLowestAncestor(findA, findB, head);

    }


}
