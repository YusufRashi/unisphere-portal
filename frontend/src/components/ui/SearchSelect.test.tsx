import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { SearchSelect, type SelectOption } from './SearchSelect'

const OPTIONS: SelectOption[] = [
  { value: 1, label: 'Dr. Ada Lovelace', description: 'STF-00001' },
  { value: 2, label: 'Prof. Alan Turing', description: 'STF-00002' },
]

function Harness({ loadOptions, exclude }: { loadOptions: (q: string) => Promise<SelectOption[]>; exclude?: number[] }) {
  const [value, setValue] = useState<SelectOption | null>(null)
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SearchSelect label="Lecturer" queryKey="test" loadOptions={loadOptions} value={value} onChange={setValue} exclude={exclude} />
      <p>Selected: {value?.label ?? 'none'}</p>
    </QueryClientProvider>
  )
}

describe('SearchSelect', () => {
  it('searches the server with what the user types', async () => {
    const loadOptions = vi.fn(async (q: string) => OPTIONS.filter((o) => o.label.toLowerCase().includes(q.toLowerCase())))
    render(<Harness loadOptions={loadOptions} />)

    await userEvent.type(screen.getByRole('combobox', { name: 'Lecturer' }), 'turing')

    // Results for the debounced query replace the initial (unfiltered) ones.
    await waitFor(() => expect(loadOptions).toHaveBeenLastCalledWith('turing'))
    await waitFor(() => expect(screen.queryByRole('option', { name: /Ada Lovelace/ })).not.toBeInTheDocument())
    expect(screen.getByRole('option', { name: /Alan Turing/ })).toBeInTheDocument()
  })

  it('selects an option with the keyboard', async () => {
    render(<Harness loadOptions={async () => OPTIONS} />)
    const input = screen.getByRole('combobox', { name: 'Lecturer' })

    await userEvent.click(input)
    await screen.findByRole('option', { name: /Ada Lovelace/ })
    await userEvent.keyboard('{ArrowDown}{Enter}')

    expect(screen.getByText('Selected: Prof. Alan Turing')).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-expanded', 'false')
  })

  it('does not offer excluded options', async () => {
    render(<Harness loadOptions={async () => OPTIONS} exclude={[1]} />)
    await userEvent.click(screen.getByRole('combobox', { name: 'Lecturer' }))

    expect(await screen.findByRole('option', { name: /Alan Turing/ })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Ada Lovelace/ })).not.toBeInTheDocument()
  })

  it('closes on Escape without selecting', async () => {
    render(<Harness loadOptions={async () => OPTIONS} />)
    const input = screen.getByRole('combobox', { name: 'Lecturer' })
    await userEvent.click(input)
    await screen.findByRole('listbox')
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(screen.getByText('Selected: none')).toBeInTheDocument()
  })
})
