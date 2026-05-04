export type UserLocationResponse = {
  address: string | null;
};

export type UserSettingsResponse = {
  displayName: string | null;
  apiKeyConfigured: boolean;
  screensaverSlideDurationSeconds: number;
};

export type UpdateUserSettingsRequest = {
  displayName?: string | null;
  openAiApiKeyOverride?: string | null;
  screensaverSlideDurationSeconds?: number | null;
};
