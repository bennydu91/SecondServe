import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";

// Mock CSS modules to return class names as-is
const cssModules = {
  sidebar: "sidebar",
  logo: "logo",
  nav: "nav",
  navItemActive: "navItemActive",
  navItem: "navItem",
  dot: "dot",
  profile: "profile",
  profileName: "profileName",
  logoutButton: "logoutButton",
};

vi.doMock("@/components/dashboard/Sidebar.module.css", () => ({ default: cssModules }));

// NewMatchForm CSS module mock
const newMatchFormCss = {
  form: "form",
  field: "field",
  error: "error",
  actions: "actions",
  cancelButton: "cancelButton",
  submitButton: "submitButton",
};

vi.doMock("@/components/console/NewMatchForm.module.css", () => ({ default: newMatchFormCss }));

// ConsoleSelectionView CSS module mock
const consoleSelectionViewCss = {
  container: "container",
  title: "title",
  section: "section",
  sectionTitle: "sectionTitle",
  empty: "empty",
  list: "list",
  listItem: "listItem",
  listItemLabel: "listItemLabel",
  resumeButton: "resumeButton",
  newMatchButton: "newMatchButton",
};

vi.doMock("@/components/console/ConsoleSelectionView.module.css", () => ({ default: consoleSelectionViewCss }));
