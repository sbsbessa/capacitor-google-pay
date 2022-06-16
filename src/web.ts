import { WebPlugin } from '@capacitor/core';

import type { GooglePayPlugin } from './definitions';

export class GooglePayWeb extends WebPlugin implements GooglePayPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
