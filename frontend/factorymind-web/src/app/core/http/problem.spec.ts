import { HttpErrorResponse } from '@angular/common/http';
import { describeError } from './problem';

describe('describeError', () => {
  it('lists field errors from a validation problem', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { title: 'Validation failed', errors: { assetCode: 'must not be blank' } },
    });
    expect(describeError(error)).toBe('assetCode: must not be blank');
  });

  it('uses the problem detail for business errors', () => {
    const error = new HttpErrorResponse({ status: 409, error: { detail: "Asset code 'M-104' is already used" } });
    expect(describeError(error)).toBe("Asset code 'M-104' is already used");
  });

  it('explains when the gateway is unreachable', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toContain('cannot be reached');
  });
});
