export interface GooglePayPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
