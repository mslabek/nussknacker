import React, { memo } from "react";
import { useSelector } from "react-redux";
import { getFetchedProcessDetails } from "../../reducers/selectors/graph";
import SpinnerWrapper from "../spinner/SpinnerWrapper";
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer";
import { useToolbarConfig } from "../toolbarSettings/useToolbarConfig";
import { NuThemeProvider } from "../../containers/theme/nuThemeProvider";

type Props = {
    isReady: boolean;
};

function Toolbars(props: Props) {
    const { isReady } = props;
    const fetchedProcessDetails = useSelector(getFetchedProcessDetails);
    const [toolbars, toolbarsConfigId] = useToolbarConfig();

    return (
        <NuThemeProvider>
            <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
                <ToolbarsLayer toolbars={toolbars} configId={toolbarsConfigId} />
            </SpinnerWrapper>
        </NuThemeProvider>
    );
}

export default memo(Toolbars);
