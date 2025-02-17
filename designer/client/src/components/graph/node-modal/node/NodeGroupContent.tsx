import { css } from "@emotion/css";
import React, { SetStateAction } from "react";
import { useSelector } from "react-redux";
import { Edge, NodeType } from "../../../../types";
import NodeUtils from "../../NodeUtils";
import { ContentSize } from "./ContentSize";
import { FragmentContent } from "./FragmentContent";
import { getNodeErrors, getPropertiesErrors } from "./selectors";
import { RootState } from "../../../../reducers";
import { NodeDetailsContent } from "../NodeDetailsContent";

interface Props {
    node: NodeType;
    edges: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => void;
}

export function NodeGroupContent({ node, edges, onChange }: Props): JSX.Element {
    const errors = useSelector((state: RootState) => {
        return node.type ? getNodeErrors(state, node.id) : getPropertiesErrors(state);
    });

    return (
        <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
            <ContentSize>
                <NodeDetailsContent node={node} edges={edges} onChange={onChange} nodeErrors={errors} showValidation showSwitch />
            </ContentSize>
            {NodeUtils.nodeIsFragment(node) && <FragmentContent nodeToDisplay={node} />}
        </div>
    );
}
