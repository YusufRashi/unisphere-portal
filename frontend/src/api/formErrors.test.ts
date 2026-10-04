import { ApiError } from './errors'
import { applyServerErrors } from './formErrors'

describe('applyServerErrors', () => {
  it('attaches known field errors to the form and returns nothing else to show', () => {
    const setError = vi.fn()
    const error = new ApiError({
      status: 400,
      code: 'VALIDATION_ERROR',
      detail: 'One or more fields are invalid.',
      errors: [{ field: 'code', message: 'may contain only letters, digits and hyphens' }],
    })

    expect(applyServerErrors(error, setError, ['code', 'name'])).toBeNull()
    expect(setError).toHaveBeenCalledWith('code', { type: 'server', message: 'May contain only letters, digits and hyphens' })
  })

  it('returns errors for fields the form does not have', () => {
    const setError = vi.fn()
    const error = new ApiError({
      status: 400,
      code: 'VALIDATION_ERROR',
      errors: [{ field: 'departmentId', message: 'must not be null' }],
    })
    expect(applyServerErrors(error, setError, ['code'])).toBe('departmentId: must not be null')
    expect(setError).not.toHaveBeenCalled()
  })

  it('returns the problem detail when there are no field errors (e.g. a 409 duplicate)', () => {
    const error = new ApiError({ status: 409, code: 'DUPLICATE_RESOURCE', detail: "Department code 'CS' already exists." })
    expect(applyServerErrors(error, vi.fn(), ['code'])).toBe("Department code 'CS' already exists.")
  })
})
