import React from "react";
import { NodeInput } from "../../../../withFocus";
import { LabeledInputProps } from "./LabeledInput";
import { NodeRow } from "../../NodeDetailsContent/NodeStyled";

export interface CheckboxProps extends Pick<LabeledInputProps, "children" | "autoFocus" | "isMarked" | "onChange" | "readOnly"> {
    value?: boolean;
    checked?: boolean;
}

export default function Checkbox(props: CheckboxProps): JSX.Element {
    const { children, autoFocus, isMarked, value, onChange, readOnly } = props;

    return (
        <NodeRow>
            {children}
            <div className={`node-value${isMarked ? " marked" : ""}${readOnly ? " read-only " : ""}`}>
                <NodeInput autoFocus={autoFocus} type="checkbox" checked={!!value} onChange={onChange} disabled={readOnly} />
            </div>
        </NodeRow>
    );
}
