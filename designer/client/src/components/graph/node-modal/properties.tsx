import { NodeType, NodeValidationError } from "../../../types";
import { useSelector } from "react-redux";
import { getScenarioPropertiesConfig } from "./NodeDetailsContent/selectors";
import React, { useMemo } from "react";
import { sortBy } from "lodash";
import { NodeTableBody } from "./NodeDetailsContent/NodeTable";
import { IdField } from "./IdField";
import { errorValidator } from "./editors/Validators";
import ScenarioProperty from "./ScenarioProperty";
import { DescriptionField } from "./DescriptionField";
import { FieldType } from "./editors/field/Field";
import { NodeField } from "./NodeField";

export function Properties({
    fieldErrors,
    isEditMode,
    node,
    renderFieldLabel,
    setProperty,
    showSwitch,
    showValidation,
}: {
    isEditMode?: boolean;
    node: NodeType;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    fieldErrors?: NodeValidationError[];
    showValidation?: boolean;
}): JSX.Element {
    const scenarioPropertiesConfig = useSelector(getScenarioPropertiesConfig);
    //fixme move this configuration to some better place?
    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const scenarioPropertiesSorted = useMemo(
        () => sortBy(Object.entries(scenarioPropertiesConfig), ([name]) => name),
        [scenarioPropertiesConfig],
    );

    return (
        <NodeTableBody>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                additionalValidators={[errorValidator(fieldErrors || [], "id")]}
            />
            {node.isFragment && (
                <NodeField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    fieldType={FieldType.input}
                    fieldLabel={"Documentation url"}
                    fieldProperty={"additionalFields.properties.docsUrl"}
                    validators={[errorValidator(fieldErrors || [], "docsUrl")]}
                    autoFocus
                />
            )}
            {scenarioPropertiesSorted.map(([propName, propConfig]) => (
                <ScenarioProperty
                    key={propName}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                    propertyName={propName}
                    propertyConfig={propConfig}
                    propertyErrors={fieldErrors || []}
                    onChange={setProperty}
                    renderFieldLabel={renderFieldLabel}
                    editedNode={node}
                    readOnly={!isEditMode}
                />
            ))}
            <DescriptionField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
            />
        </NodeTableBody>
    );
}
