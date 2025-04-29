package kr.study.systemdesign.consistenthash;

import kr.study.systemdesign.consistenthash.type.NodeType;

public interface Node {
    String getId();
    Node getNode();
    NodeType getNodeType();

    class PhysicalNode implements Node {
        private final String id;

        public PhysicalNode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Node getNode() {
            return this;
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.PHYSICAL;
        }
    }


    class VirtualNode implements Node {
        private final PhysicalNode physicalNode;
        private final String id;

        public VirtualNode(PhysicalNode physicalNode, int replicaIndex) {
            this.physicalNode = physicalNode;
            this.id = physicalNode.getId() + "#" + replicaIndex;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Node getNode() {
            return physicalNode;
        }

        @Override
        public NodeType getNodeType() {
            return NodeType.VIRTUAL;
        }
    }

}

