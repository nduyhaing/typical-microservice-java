import { ApiError } from './ApiClientService';
import { toErrorInfo } from './errors';

describe('toErrorInfo', () => {
  it('400 shows the ErrorVm detail and field errors', () => {
    const error = new ApiError(400, {
      statusCode: '400 BAD_REQUEST',
      title: 'Bad Request',
      detail: 'Request information is not valid',
      fieldErrors: ['name must not be blank'],
    });
    expect(toErrorInfo(error)).toEqual({
      message: 'Request information is not valid',
      fieldErrors: ['name must not be blank'],
    });
  });

  it('401 asks to log in again', () => {
    expect(toErrorInfo(new ApiError(401, {})).message).toBe('Your session has expired. Reload the page to log in again.');
  });

  it('403 says the action needs an administrator', () => {
    expect(toErrorInfo(new ApiError(403, {})).message).toBe('You are not allowed to do this. The back office is for administrators only.');
  });

  it('other errors get a generic message', () => {
    expect(toErrorInfo(new ApiError(500, '<html>')).message).toBe('Something went wrong. Please try again later.');
    expect(toErrorInfo(new Error('network')).fieldErrors).toEqual([]);
  });
});
