import React from "react";
import ReactDOM from "react-dom";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import NussknackerInitializer from "./containers/NussknackerInitializer";
import { SettingsProvider } from "./containers/SettingsInitializer";
import "./i18n";
import { StoreProvider } from "./store/provider";
import rootRoutes from "./containers/RootRoutes";
import { BASE_PATH } from "./config";
import { css } from "@emotion/css";
import RootErrorBoundary from "./components/common/RootErrorBoundary";

const rootContainer = document.createElement(`div`);
rootContainer.id = "root";
rootContainer.className = css({
    height: "100dvh",
    display: "flex",
});
document.body.appendChild(rootContainer);

const router = createBrowserRouter(rootRoutes, { basename: BASE_PATH.replace(/\/$/, "") });

const Root = () => {
    return (
        <RootErrorBoundary>
            <StoreProvider>
                <SettingsProvider>
                    <NussknackerInitializer>
                        <RouterProvider router={router} />
                    </NussknackerInitializer>
                </SettingsProvider>
            </StoreProvider>
        </RootErrorBoundary>
    );
};

ReactDOM.render(<Root />, rootContainer);
