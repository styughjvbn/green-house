import { defineConfig, globalIgnores } from "eslint/config";
import { readdirSync } from "node:fs";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const featureNames = readdirSync(new URL("./src/features", import.meta.url), {
  withFileTypes: true,
})
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name);

const featureBoundaryConfigs = featureNames.map((featureName) => ({
  name: `greenhouse/feature-boundary/${featureName}`,
  files: [`src/features/${featureName}/**/*.{js,jsx,ts,tsx}`],
  rules: {
    "no-restricted-imports": [
      "error",
      {
        patterns: [
          {
            regex: "^@/(?:app|widgets)/",
            message: "features may not depend on app or widgets",
          },
          {
            regex: `^@/features/(?!${featureName}(?:/|$))[^/]+/.+`,
            message: "import another feature through its public index",
          },
        ],
      },
    ],
  },
}));

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    name: "greenhouse/app-feature-public-api",
    files: ["src/app/**/*.{js,jsx,ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              regex: "^@/features/[^/]+/.+",
              message:
                "app routes must import features through their public index",
            },
          ],
        },
      ],
    },
  },
  {
    name: "greenhouse/shared-layer-boundary",
    files: ["src/shared/**/*.{js,jsx,ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              regex: "^@/(?:app|features|entities|widgets)/",
              message: "shared may not depend on higher application layers",
            },
          ],
        },
      ],
    },
  },
  {
    name: "greenhouse/entity-layer-boundary",
    files: ["src/entities/**/*.{js,jsx,ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              regex: "^@/(?:app|features|widgets)/",
              message: "entities may not depend on app, features, or widgets",
            },
          ],
        },
      ],
    },
  },
  {
    name: "greenhouse/widget-layer-boundary",
    files: ["src/widgets/**/*.{js,jsx,ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              regex: "^@/app/",
              message: "widgets may not depend on app routes",
            },
            {
              regex: "^@/features/[^/]+/.+",
              message:
                "widgets must import features through their public index",
            },
          ],
        },
      ],
    },
  },
  ...featureBoundaryConfigs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    "src/shared/api/generated/openapi.d.ts",
  ]),
]);

export default eslintConfig;
